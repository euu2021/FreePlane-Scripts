/*
This script is interesting because it shows hoy to replace a listener in the userInputListenerFactory.
The script is not finished, so it has some bugs. It was abandoned because the feature was introduced in vanilla FP: https://github.com/freeplane/freeplane/issues/2369#issuecomment-2781562492
*/


import org.freeplane.core.resources.ResourceController
import org.freeplane.core.ui.DoubleClickTimer
import org.freeplane.core.ui.IMouseListener
import org.freeplane.core.ui.components.AutoHide
import org.freeplane.core.util.Compat
import org.freeplane.core.util.LogUtils
import org.freeplane.features.icon.IconController
import org.freeplane.features.icon.NamedIcon
import org.freeplane.features.link.LinkController
import org.freeplane.features.map.FoldingController
import org.freeplane.features.map.MapController
import org.freeplane.features.map.NodeModel
import org.freeplane.features.mode.Controller
import org.freeplane.features.mode.ModeController
import org.freeplane.view.swing.map.MainView
import org.freeplane.view.swing.map.MapView
import org.freeplane.view.swing.map.MouseArea
import org.freeplane.view.swing.map.NodeView

import javax.swing.*
import java.awt.*
import java.awt.event.MouseEvent

import org.freeplane.view.swing.ui.*
import org.freeplane.view.swing.map.*

import java.lang.reflect.Field


def factory = Controller.getCurrentModeController().userInputListenerFactory
Field field = factory.getClass().getDeclaredField("nodeMouseMotionListener")
field.setAccessible(true)
field.set(factory, null)

factory.setNodeMouseMotionListener(new DefaultNodeMouseMotionListener2())

c.viewRoot.findAll().each {

    mapView = Controller.currentController.MapViewManager.mapView
    NodeView nv = mapView.getNodeView(it.delegate)
    if (nv == null) return

    nvMainView = nv.mainView
    MainView newMainView = NodeViewFactory.getInstance().newMainView(nv);
    nv.setMainView(newMainView);

}


public class DefaultNodeMouseMotionListener2 implements IMouseListener {
    protected final NodeSelector nodeSelector;
    private static final String FOLD_ON_CLICK_INSIDE = "fold_on_click_inside";
    static final String OPEN_LINKS_ON_PLAIN_CLICKS = "openLinksOnPlainClicks";

    boolean foldOnHoverLock = false

    /**
     * The mouse has to stay in this region to enable the selection after a
     * given time.
     */
    protected final DoubleClickTimer doubleClickTimer;
    private boolean popupMenuIsShown;

    public DefaultNodeMouseMotionListener2() {
//		mc = modeController;
        doubleClickTimer = new DoubleClickTimer();
        nodeSelector = new NodeSelector();

    }


    protected boolean isInFoldingRegion(MouseEvent e) {

        return ((MainView)e.getComponent()).isInFoldingRegion(e.getPoint());
    }

    protected boolean isInDragRegion(MouseEvent e) {

        return ((MainView)e.getComponent()).isInDragRegion(e.getPoint());
    }

    @Override
    public void mouseClicked(final MouseEvent e) {

        if(popupMenuIsShown){
            return;
        }
        final MainView component = (MainView) e.getComponent();
        NodeView nodeView = component.getNodeView();
        if (nodeView == null)
            return;

        final NodeModel node = nodeView.getNode();
        Controller currentController = Controller.getCurrentController();
        final ModeController mc = currentController.getModeController();
        final MapController mapController = mc.getMapController();
        if(e.getButton() == MouseEvent.BUTTON1
                && Compat.isPlainEvent(e)
                && isInFoldingRegion(e)) {
            doubleClickTimer.cancel();
            MouseEventActor.INSTANCE.withMouseEvent( () ->
                    mapController.toggleFoldedAndScroll(node));
            return;
        }

        boolean isDelayedFoldingActive = false;
        final boolean inside = nodeSelector.isInside(e);
        Point point = e.getPoint();
        if(e.getButton() == 1){
            if(Compat.isCtrlEvent(e) || Compat.isPlainEvent(e) && ResourceController.getResourceController().getBooleanProperty(OPEN_LINKS_ON_PLAIN_CLICKS)){
                NamedIcon uiIcon = component.getUIIconAt(point);
                if(uiIcon != null){
                    final IconController iconController = mc.getExtension(IconController.class);
                    if(iconController.onIconClicked(node, uiIcon))
                        return;
                }
                if (component.isClickableLink(point)) {
                    LinkController.getController(mc).loadURL(node, e);
                    e.consume();
                    return;
                }


                final String link = component.getLink(point);
                if (link != null) {
                    doubleClickTimer.start(new Runnable() {
                        @Override
                        public void run() {
                            loadLink(node, link);
                        }
                    });
                    e.consume();
                    return;
                }
            }
            else if(Compat.isShiftEvent(e)){
                if (component.isClickableLink(point)) {
                    mapController.forceViewChange(() -> LinkController.getController(mc).loadURL(node, e));
                    e.consume();
                    return;
                }

                final String link = component.getLink(point);
                if (link != null) {
                    doubleClickTimer.start(new Runnable() {
                        @Override
                        public void run() {
                            mapController.forceViewChange(() -> loadLink(node, link));
                        }
                    });
                    e.consume();
                    return;
                }
            }

            if(Compat.isPlainEvent(e)){
                if(inside && (e.getClickCount() == 1 && foldsOnClickInside()
                        || ! (mc.canEdit(node.getMap()) && editsOnDoubleClick()))){
                    if (!nodeSelector.shouldSelectOnClick(e)) {
                        isDelayedFoldingActive = true;
                        doubleClickTimer.start(new Runnable() {
                            @Override
                            public void run() {
                                MouseEventActor.INSTANCE.withMouseEvent( () ->
                                        mapController.toggleFoldedAndScroll(node));
                            }
                        });
                    }
                }
            }
            else if(Compat.isShiftEvent(e)){
                if (isInFoldingRegion(e)) {
                    if (! mapController.showNextChild(node))
                        mapController.fold(node);
                    e.consume();
                }
            }
        }

        if (inside && Compat.isCtrlShiftEvent(e) && !nodeSelector.shouldSelectOnClick(e)) {
            doubleClickTimer.cancel();
            MouseEventActor.INSTANCE.withMouseEvent( () ->
                    mapController.toggleFoldedAndScroll(node));
            e.consume();
            return;
        }

        if(inside && e.getButton() == 1 &&  ! e.isAltDown()) {
            nodeSelector.extendSelection(e, ! isDelayedFoldingActive);
        }
    }


    private boolean foldsOnClickInside() {
        return ResourceController.getResourceController().getBooleanProperty(FOLD_ON_CLICK_INSIDE);
    }

    protected boolean editsOnDoubleClick() {
        return false;
    }


    private void loadLink(NodeModel node, final String link) {
        try {
            LinkController.getController().loadURI(node, LinkController.createHyperlink(link));
        } catch (Exception ex) {
            LogUtils.warn(ex);
        }
    }

    /**
     * Invoked when a mouse button is pressed on a component and then
     * dragged.
     */
    @Override
    public void mouseDragged(final MouseEvent e) {

        if (!nodeSelector.isInside(e))
            return;
        nodeSelector.stopTimerForDelayedSelection();
        nodeSelector.extendSelection(e, false);
    }

    @Override
    public void mouseEntered(final MouseEvent e) {

        foldOnHoverLock = false

        if (nodeSelector.isRelevant(e)) {


            nodeSelector.createTimer(e);
            mouseMoved(e);
        }
    }

    @Override
    public void mouseExited(final MouseEvent e) {
        foldOnHoverLock = false

        nodeSelector.stopTimerForDelayedSelection();
        final MainView v = (MainView) e.getSource();
        v.setMouseArea(MouseArea.OUT);
        nodeSelector.trackWindowForComponent(v);
    }

    @Override
    public void mouseMoved(final MouseEvent e) {

        if (!nodeSelector.isRelevant(e))
            return;
        final MainView node = ((MainView) e.getComponent());
        Point point = e.getPoint();
        String link = node.getLink(point);
        boolean followLink = link != null;
        Controller currentController = Controller.getCurrentController();
        if(! followLink){
            followLink = node.isClickableLink(point);
            if(followLink){
                link = LinkController.getController(currentController.getModeController()).getLinkShortText(node.getNodeView().getNode());
            }
        }
        final Cursor requiredCursor;
        if(followLink){
            currentController.getViewController().out(link);
            requiredCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            node.setMouseArea(MouseArea.LINK);
        }
        else if (isInFoldingRegion(e)){

            if(!foldOnHoverLock) {

                node.getNodeView().setFolded(!node.getNodeView().isFolded())

                foldOnHoverLock = true
            }

            requiredCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
            node.setMouseArea(MouseArea.FOLDING);
        }
        else{
            requiredCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
            node.setMouseArea(MouseArea.DEFAULT);
        }
        if (node.getCursor().getType() != requiredCursor.getType() || requiredCursor.getType() == Cursor.CUSTOM_CURSOR && node.getCursor() != requiredCursor) {
            node.setCursor(requiredCursor);
        }
        nodeSelector.createTimer(e);
    }

    @Override
    public void mousePressed(final MouseEvent e) {

        final MapView mapView = MapView.getMapView(e.getComponent());
        mapView.select();
        doubleClickTimer.cancel();
        popupMenuIsShown = false;
        if (Compat.isPopupTrigger(e))
            showPopupMenu(e);
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
        nodeSelector.stopTimerForDelayedSelection();
        if (Compat.isPopupTrigger(e))
            showPopupMenu(e);
    }

    private void showPopupMenu(final MouseEvent e) {
        popupMenuIsShown = true;
        final boolean inside = nodeSelector.isInside(e);
        final boolean inFoldingRegion = ! inside && isInFoldingRegion(e);
        if (inside || inFoldingRegion) {
            if(inside){
                nodeSelector.stopTimerForDelayedSelection();
                new NodePopupMenuDisplayer().showNodePopupMenu(e);
            }
            else if(inFoldingRegion){
                showFoldingPopup(e);
            }
        }
    }

    private void showFoldingPopup(MouseEvent e) {

        ModeController mc = Controller.getCurrentController().getModeController();
        final FoldingController foldingController = mc.getExtension(FoldingController.class);
        if(foldingController == null)
            return;
        final NodeView nodeView = nodeSelector.getRelatedNodeView(e);
        final JPopupMenu popupmenu = foldingController.createFoldingPopupMenu(nodeView.getNode());
        AutoHide.start(popupmenu);
        new NodePopupMenuDisplayer().showMenuAndConsumeEvent(popupmenu, e);
    }

}
