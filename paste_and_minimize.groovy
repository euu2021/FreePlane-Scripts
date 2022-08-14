// @ExecutionModes({ON_SINGLE_NODE})

menuUtils.executeMenuItems(['PasteAction',])

def max_shortened_text_length = config.getIntProperty("max_shortened_text_length") 

def createdSince = new Date()
createdSince.setSeconds(createdSince.getSeconds() - 1);

def matches = new ArrayList(c.find{ it.CreatedAt.after(createdSince) })
matches.each{
if (it.to.plain.size() > max_shortened_text_length)         it.setMinimized(true)     
else         it.setMinimized(false) }
