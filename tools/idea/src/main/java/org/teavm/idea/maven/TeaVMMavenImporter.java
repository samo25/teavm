/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.idea.maven;

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jdom.Element;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.teavm.idea.TeaVMFacet;
import org.teavm.idea.TeaVMFacetConfiguration;
import org.teavm.idea.TeaVMFacetType;
import org.teavm.idea.TeaVMWebAssemblyFacetType;
import org.teavm.idea.jps.model.TeaVMJpsConfiguration;
import org.teavm.tooling.TeaVMTargetType;

public class TeaVMMavenImporter extends MavenImporter {
    public TeaVMMavenImporter() {
        super("org.teavm", "teavm-maven-plugin");
    }

    @Override
    public void preProcess(Module module, MavenProject mavenProject, MavenProjectChanges changes,
            IdeModifiableModelsProvider modifiableModelsProvider) {
    }

    @Override
    public void process(IdeModifiableModelsProvider modifiableModelsProvider, Module module,
            MavenRootModelAdapter rootModel, MavenProjectsTree mavenModel, MavenProject mavenProject,
            MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName,
            List<MavenProjectsProcessorTask> postTasks) {
        FacetManager facetManager = FacetManager.getInstance(module);

        Set<String> targetTypes = new HashSet<>();
        for (MavenPlugin mavenPlugin : mavenProject.getPlugins()) {
            if (mavenPlugin.getGroupId().equals(myPluginGroupID)
                    && mavenPlugin.getArtifactId().equals(myPluginArtifactID)) {
                updateConfiguration(mavenPlugin, facetManager, module, targetTypes);
            }
        }
    }

    private void updateConfiguration(MavenPlugin plugin, FacetManager facetManager, Module module,
            Set<String> targetTypes) {
        if (plugin.getConfigurationElement() != null) {
            updateConfiguration(plugin.getConfigurationElement(), facetManager, module, targetTypes);
        }

        for (MavenPlugin.Execution execution : plugin.getExecutions()) {
            if (execution.getGoals().contains("compile")) {
                if (execution.getConfigurationElement() != null) {
                    updateConfiguration(execution.getConfigurationElement(), facetManager, module, targetTypes);
                }
            }
        }
    }

    private void updateConfiguration(Element source, FacetManager facetManager, Module module,
            Set<String> targetTypes) {
        FacetType<TeaVMFacet, TeaVMFacetConfiguration> facetType;
        switch (getTargetType(source)) {
            case JAVASCRIPT:
                facetType = TeaVMFacetType.getInstance();
                break;
            case WEBASSEMBLY:
                facetType = TeaVMWebAssemblyFacetType.getInstance();
                break;
            default:
                return;
        }

        if (!targetTypes.add(facetType.getStringId())) {
            return;
        }

        TeaVMFacet facet = facetManager.getFacetByType(facetType.getId());
        if (facet == null) {
            facet = new TeaVMFacet(facetType, module, facetType.getDefaultFacetName(),
                    facetType.createDefaultConfiguration(), null);
            facetManager.addFacet(facetType, facet.getName(), facet);
        }

        TeaVMJpsConfiguration configuration = facet.getConfiguration().getState();

        for (Element child : source.getChildren()) {
            switch (child.getName()) {
                case "sourceFilesCopied":
                    configuration.setSourceFilesCopied(Boolean.parseBoolean(child.getTextTrim()));
                    break;
                case "sourceMapsGenerated":
                    configuration.setSourceMapsFileGenerated(Boolean.parseBoolean(child.getTextTrim()));
                    break;
                case "minifying":
                    configuration.setMinifying(Boolean.parseBoolean(child.getTextTrim()));
                    break;
                case "targetDirectory":
                    configuration.setTargetDirectory(child.getTextTrim());
                    break;
                case "mainClass":
                    configuration.setMainClass(child.getTextTrim());
                    break;
            }
        }

        facet.getConfiguration().loadState(configuration);
    }

    private TeaVMTargetType getTargetType(Element source) {
        for (Element child : source.getChildren()) {
            if (child.getName().equals("targetType")) {
                try {
                    return TeaVMTargetType.valueOf(child.getTextTrim());
                } catch (IllegalArgumentException e) {
                    // do nothing, continue iterating
                }
            }
        }
        return TeaVMTargetType.JAVASCRIPT;
    }
}
