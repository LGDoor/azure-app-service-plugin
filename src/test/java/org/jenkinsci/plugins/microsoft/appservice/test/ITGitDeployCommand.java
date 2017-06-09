/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package org.jenkinsci.plugins.microsoft.appservice.test;

import com.microsoft.azure.management.appservice.*;
import com.microsoft.azure.management.resources.ResourceGroup;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.microsoft.appservice.commands.GitDeployCommand;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ITGitDeployCommand extends IntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(ITGitDeployCommand.class.getName());
    private GitDeployCommand command = null;
    private GitDeployCommand.IGitDeployCommandData commandDataMock = null;
    private AppServicePlan appServicePlan = null;
    private FilePath workspace = null;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        command = new GitDeployCommand();
        commandDataMock = mock(GitDeployCommand.IGitDeployCommandData.class);
        setUpBaseCommandMockErrorHandling(commandDataMock);

        // Create resource group
        final ResourceGroup resourceGroup = customTokenCache.getAzureClient().resourceGroups()
                .define(testEnv.azureResourceGroup)
                .withRegion(testEnv.azureLocation)
                .create();
        Assert.assertNotNull(resourceGroup);

        // Create app service plan
        appServicePlan = customTokenCache.getAzureClient().appServices().appServicePlans()
                .define(testEnv.appServicePlanName)
                .withRegion(testEnv.azureLocation)
                .withNewResourceGroup(testEnv.azureResourceGroup)
                .withPricingTier(testEnv.appServicePricingTier)
                .create();
        Assert.assertNotNull(appServicePlan);

        // Create workspace
        File workspaceDir = com.google.common.io.Files.createTempDir();
        workspaceDir.deleteOnExit();
        workspace = new FilePath(workspaceDir);

        // Mock build
        final AbstractBuild build = mock(AbstractBuild.class);
        when(build.getWorkspace()).thenReturn(workspace);
        final EnvVars env = new EnvVars("BUILD_TAG", "jenkins-job-1");
        try {
            when(build.getEnvironment(any(TaskListener.class))).thenReturn(env);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        when(commandDataMock.getBuild()).thenReturn(build);

        // Mock build listener
        final BuildListener listener = new StreamBuildListener(System.out, Charset.defaultCharset());
        when(commandDataMock.getListener()).thenReturn(listener);
    }

    /**
     * This test deploys a NodeJS application
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void deployNodeJS() throws IOException, InterruptedException {
        final WebApp webApp = customTokenCache.getAzureClient().appServices().webApps()
                .define(testEnv.appServiceName)
                .withExistingResourceGroup(testEnv.azureResourceGroup)
                .withExistingAppServicePlan(appServicePlan)
                .create();
        Assert.assertNotNull(webApp);
        when(commandDataMock.getPublishingProfile()).thenReturn(webApp.getPublishingProfile());

        Utils.extractResourceFile(getClass(), "sample-nodejs-app/index.js", workspace.child("index.js").getRemote());
        Utils.extractResourceFile(getClass(), "sample-nodejs-app/package.json", workspace.child("package.json").getRemote());
        Utils.extractResourceFile(getClass(), "sample-nodejs-app/process.json", workspace.child("process.json").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("*.js,*.json");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()),"Hello NodeJS!", 300);
    }

    /**
     * This test deploys a PHP application
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void deployPHP() throws IOException, InterruptedException {
        final WebApp webApp = customTokenCache.getAzureClient().appServices().webApps()
                .define(testEnv.appServiceName)
                .withExistingResourceGroup(testEnv.azureResourceGroup)
                .withExistingAppServicePlan(appServicePlan)
                .withPhpVersion(PhpVersion.PHP5_6)
                .create();
        Assert.assertNotNull(webApp);
        when(commandDataMock.getPublishingProfile()).thenReturn(webApp.getPublishingProfile());

        Utils.extractResourceFile(getClass(), "sample-php-app/index.php", workspace.child("index.php").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("*.php");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()),"Hello PHP!", 300);
    }

    /**
     * This test deploys a Python application
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void deployPython() throws IOException, InterruptedException {
        final WebApp webApp = customTokenCache.getAzureClient().appServices().webApps()
                .define(testEnv.appServiceName)
                .withExistingResourceGroup(testEnv.azureResourceGroup)
                .withExistingAppServicePlan(appServicePlan)
                .withPythonVersion(PythonVersion.PYTHON_34)
                .create();
        Assert.assertNotNull(webApp);
        when(commandDataMock.getPublishingProfile()).thenReturn(webApp.getPublishingProfile());

        Utils.extractResourceFile(getClass(), "sample-python-app/main.py", workspace.child("main.py").getRemote());
        Utils.extractResourceFile(getClass(), "sample-python-app/virtualenv_proxy.py", workspace.child("virtualenv_proxy.py").getRemote());
        Utils.extractResourceFile(getClass(), "sample-python-app/requirements.txt", workspace.child("requirements.txt").getRemote());
        Utils.extractResourceFile(getClass(), "sample-python-app/web.3.4.config", workspace.child("web.3.4.config").getRemote());
        when(commandDataMock.getFilePath()).thenReturn("*.py,*.config,requirements.txt");

        command.execute(commandDataMock);

        Utils.waitForAppReady(new URL("https://" + webApp.defaultHostName()),"Hello, Python!", 300);
    }
}