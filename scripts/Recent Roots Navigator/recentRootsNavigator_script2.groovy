//script 2

//SPDX-License-Identifier: GPL-3.0-or-later

import groovy.transform.SourceURI
import java.net.URI

script1FileName = "recentRootsNavigator_script1.groovy"

@SourceURI
URI scriptUri

scriptDir = new File(scriptUri).parentFile

File script1 = new File(scriptDir, script1FileName)

c.script(script1).withAllPermissions().executeOn(node)
