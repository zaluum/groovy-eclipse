 /*
 * Copyright 2003-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.eclipse.editor;

import org.codehaus.groovy.eclipse.editor.highlighting.HighlightingExtenderRegistry;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;

/**
 * @author Andrew Eisenberg
 * @created Jul 22, 2009
 * Modeled after JavaTextTools
 * Shared text tools for GroovyEditors
 */
public class GroovyTextTools {

    private final GroovyColorManager colorManager = new GroovyColorManager();

    private IPartitionTokenScanner partitionScanner;

    private HighlightingExtenderRegistry highlightingExtenderRegistry;

    public GroovyColorManager getColorManager() {
        return colorManager;
    }

    public void dispose() {
        colorManager.dispose();
        highlightingExtenderRegistry = null;
        partitionScanner = null;
    }

    public IPartitionTokenScanner getGroovyPartitionScanner() {
        if (partitionScanner == null) {
            partitionScanner = new GroovyPartitionScanner();
        }
        return partitionScanner;
    }

    public HighlightingExtenderRegistry getHighlightingExtenderRegistry() {
        if (highlightingExtenderRegistry == null) {
            highlightingExtenderRegistry = new HighlightingExtenderRegistry();
            highlightingExtenderRegistry.initialize();
        }
        return highlightingExtenderRegistry;
    }
}