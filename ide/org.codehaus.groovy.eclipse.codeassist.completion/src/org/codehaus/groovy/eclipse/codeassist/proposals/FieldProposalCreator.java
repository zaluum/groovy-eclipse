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

package org.codehaus.groovy.eclipse.codeassist.proposals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.ImportNodeCompatibilityWrapper;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.eclipse.codeassist.ProposalUtils;
import org.eclipse.jdt.groovy.search.VariableScope;
import org.objectweb.asm.Opcodes;

/**
 * @author Andrew Eisenberg
 * @created Nov 12, 2009
 *
 * GRECLIPSE-512: most fields have properties created for them, so we want to
 * avoid duplicate proposals.
 * Constants are an exception, so return them here.
 * I may have missed something, so be prepared to add more kinds of fields here.
 */
public class FieldProposalCreator extends AbstractProposalCreator implements IProposalCreator {

    private static final GroovyFieldProposal CLASS_PROPOSAL = createClassProposal();

    private static GroovyFieldProposal createClassProposal() {
        FieldNode field = new FieldNode("class", Opcodes.ACC_PUBLIC & Opcodes.ACC_STATIC & Opcodes.ACC_FINAL, VariableScope.CLASS_CLASS_NODE, VariableScope.OBJECT_CLASS_NODE, null);
        field.setDeclaringClass(VariableScope.OBJECT_CLASS_NODE);
        return new GroovyFieldProposal(field);
    }

    public List<IGroovyProposal> findAllProposals(ClassNode type,
            Set<ClassNode> categories, String prefix, boolean isStatic) {
        Collection<FieldNode> allFields = getAllFields(type);
        List<IGroovyProposal> groovyProposals = new LinkedList<IGroovyProposal>();
        for (FieldNode field : allFields) {
            // in static context, only allow static fields
            if ((!isStatic || field.isStatic()) &&
                    ProposalUtils.looselyMatches(prefix, field.getName())) {
                GroovyFieldProposal fieldProposal = new GroovyFieldProposal(field);
                float relevanceMultiplier = isInterestingType(field
                        .getType()) ? 11 : 1;
                relevanceMultiplier *= field.isStatic() ? 0.1 : 1;
                fieldProposal.setRelevanceMultiplier(relevanceMultiplier);
                groovyProposals.add(fieldProposal);
            }
        }

        if (isStatic && "class".startsWith(prefix)) {
            groovyProposals.add(CLASS_PROPOSAL);
        }

        // now add all proposals coming from static imports
        ClassNode enclosingTypeDeclaration = currentScope
                .getEnclosingTypeDeclaration();
        if (enclosingTypeDeclaration != null
                && type.getName().equals(enclosingTypeDeclaration.getName())) {
            groovyProposals.addAll(getStaticImportProposals(prefix,
                    type.getModule()));
        }

        return groovyProposals;
    }

    private List<IGroovyProposal> getStaticImportProposals(
            String prefix, ModuleNode module) {
        List<IGroovyProposal> staticProposals = new ArrayList<IGroovyProposal>();
        Map<String, ImportNode> staticImports = ImportNodeCompatibilityWrapper
                .getStaticImports(module);
        for (Entry<String, ImportNode> entry : staticImports.entrySet()) {
            String fieldName = entry.getValue().getFieldName();
            if (fieldName != null
                    && ProposalUtils.looselyMatches(prefix, fieldName)) {
                FieldNode field = entry.getValue().getType()
                        .getField(fieldName);
                if (field != null) {
                    staticProposals.add(new GroovyFieldProposal(field));
                }
            }
        }
        Map<String, ImportNode> staticStarImports = ImportNodeCompatibilityWrapper
                .getStaticStarImports(module);
        for (Entry<String, ImportNode> entry : staticStarImports.entrySet()) {
            ClassNode type = entry.getValue().getType();
            if (type != null) {
                for (FieldNode field : (Iterable<FieldNode>) type.getFields()) {
                    if (field.isStatic()
                            && ProposalUtils.looselyMatches(prefix,
                                    field.getName())) {
                        staticProposals.add(new GroovyFieldProposal(field));
                    }
                }
            }
        }

        return staticProposals;
    }

    /**
     * returns all fields, even those that are converted into properties
     * @param thisType
     * @return
     * @see http://docs.codehaus.org/display/GROOVY/Groovy+Beans
     */
    private Collection<FieldNode> getAllFields(ClassNode thisType) {
        // use a LinkedHashSet to preserve order
        Set<ClassNode> types = new LinkedHashSet<ClassNode>();
        getAllSupers(thisType, types);
        Map<String, FieldNode> nameFieldMap = new HashMap<String, FieldNode>();
        for (ClassNode type : types) {
            for (FieldNode field : type.getFields()) {
                if (checkName(field.getName())) {
                    // only add new field if the new field is more accessible than the existing one
                    FieldNode existing = nameFieldMap.get(field.getName());
                    if (existing == null || leftIsMoreAccessible(field, existing)) {
                        nameFieldMap.put(field.getName(), field);
                    }
                }
            }
        }
        return nameFieldMap.values();
    }

    /**
     * find the most accessible element
     */
    private boolean leftIsMoreAccessible(FieldNode field, FieldNode existing) {
        int leftAcc;
        switch (field.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) {
            case Opcodes.ACC_PUBLIC:
                leftAcc = 0;
                break;
            case Opcodes.ACC_PROTECTED:
                leftAcc = 1;
                break;
            case Opcodes.ACC_PRIVATE:
                leftAcc = 3;
                break;
            default: // package default
                leftAcc = 2;
                break;
        }

        int rightAcc;
        switch (existing.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) {
            case Opcodes.ACC_PUBLIC:
                rightAcc = 0;
                break;
            case Opcodes.ACC_PROTECTED:
                rightAcc = 1;
                break;
            case Opcodes.ACC_PRIVATE:
                rightAcc = 3;
                break;
            default: // package default
                rightAcc = 2;
                break;
        }
        return leftAcc < rightAcc;
    }
}