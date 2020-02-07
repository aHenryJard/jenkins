package jenkins.model;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.recipes.WithPlugin;

import hudson.PluginWrapper;
import hudson.cli.CLICommandInvoker;
import hudson.cli.DisablePluginCommand;
import hudson.model.Descriptor;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Shell;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;

/**
 * As Jenkins.MANAGE can be enabled on startup with jenkins.permission.manage.enabled property, we need a test class
 * with this property activated.
 */
public class JenkinsManagePermissionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    static {
        // this happens before the Jenkins static fields are loaded
        System.setProperty("jenkins.permission.manage.enabled", "true");
    }

    // -------------------------
    // Moved from hudson/model/labels/LabelAtomPropertyTest.java
    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Issue("JENKINS-60266")
    @Test
    public void configAllowedWithManagePermission() throws Exception {
        final String MANAGER = "manager";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to(MANAGER));

        LabelAtom label = j.jenkins.getLabelAtom("foo");

        // it should survive the configuration roundtrip
        HtmlForm labelConfigForm = j.createWebClient().login(MANAGER).goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");
        j.submit(labelConfigForm);

        assertEquals("example description",label.getDescription());
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Issue("JENKINS-60266")
    @Test
    public void configForbiddenWithoutManageOrAdminPermissions() throws Exception {
        final String UNAUTHORIZED = "reader";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(UNAUTHORIZED));

        j.jenkins.getLabelAtom("foo");

        // Unauthorized user can't be able to access the configuration form
        JenkinsRule.WebClient webClient = j.createWebClient().login(UNAUTHORIZED).withThrowExceptionOnFailingStatusCode(false);
        webClient.assertFails("label/foo/configure", 403);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.ADMINISTER).everywhere().to(UNAUTHORIZED));

        // And can't submit the form neither
        HtmlForm labelConfigForm = webClient.goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(UNAUTHORIZED));
        HtmlPage submitted = j.submit(labelConfigForm);
        assertEquals(403, submitted.getWebResponse().getStatusCode());
    }
    // End of Moved from hudson/model/labels/LabelAtomPropertyTest.java
    //-------


    // -----------------------------
    //Moved from DisablePluginCommandTest
    @Issue("JENKINS-60266")
    @Test
    @WithPlugin({ "depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    public void managerCanNotDisablePlugin() {

        //GIVEN a user with Jenkins.MANAGE permission
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        //WHEN trying to disable a plugin
        assertThat(disablePluginsCLiCommandAs("manager", "dependee"), failedWith(6));
        //THEN it's refused and the plugin is not disabled.
        assertPluginEnabled("dependee");
    }

    /**
     * Disable a list of plugins using the CLI command.
     * @param user Username
     * @param args Arguments to pass to the command.
     * @return Result of the command. 0 if succeed, 16 if some plugin couldn't be disabled due to dependent plugins.
     */
    private CLICommandInvoker.Result disablePluginsCLiCommandAs(String user, String... args) {
        return new CLICommandInvoker(j, new DisablePluginCommand()).asUser(user).invokeWithArgs(args);
    }


    private void assertPluginEnabled(String name) {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        assertTrue(plugin.isEnabled());
    }

    // End of Moved from DisablePluginCommandTest
    //-------

    // -----------------------------
    //Moved from ComputerTest
    @Issue("JENKINS-60266")
    @Test
    public void dumpExportTableForbiddenWithoutAdminPermission() throws Exception {
        final String READER = "reader";
        final String MANAGER = "manager";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(READER)
                                                   .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
                                                   .grant(Jenkins.READ).everywhere().to(MANAGER)
        );
        j.createWebClient().login(READER).assertFails("computer/(master)/dumpExportTable", 403);
        j.createWebClient().login(MANAGER).assertFails("computer/(master)/dumpExportTable", 403);
    }

    // End of Moved from ComputerTest
    //-------

    // -----------------------------
    //Moved from HusdonTest
    @Issue("JENKINS-60266")
    @Test
    public void someGlobalConfigurationIsNotDisplayedWithManagePermission() throws Exception {
        //GIVEN a user with Jenkins.MANAGE permission
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().toEveryone());

        //WHEN the user goes to /configure page
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        String formText = form.asText();
        //THEN items restricted to ADMINISTER only should not be displayed.
        assertThat("Shouldn't be able to configure # of executors", formText, not(containsString("executors")));
        assertThat("Shouldn't be able to configure Global properties", formText,
                   not(containsString("Global properties")));
        assertThat("Shouldn't be able to configure Administrative monitors", formText, not(containsString(
                "Administrative "
                + "monitors")));
        assertThat("Shouldn't be able to configure Shell", formText, not(containsString("Shell")));
    }

    @Issue("JENKINS-60266")
    @Test
    public void someGlobalConfigCanNotBeModifiedWithManagePermission() throws Exception {
        //GIVEN the Global Configuration Form, with some changes unsaved
        int currentNumberExecutors = j.getInstance().getNumExecutors();
        String shell = getShell();
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        form.getInputByName("_.numExecutors").setValueAttribute(""+(currentNumberExecutors+1));
        form.getInputByName("_.shell").setValueAttribute("/fakeShell");

        // WHEN a user with Jenkins.MANAGE permission only try to save those changes
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().toEveryone());
        j.submit(form);
        // THEN the changes on fields forbidden to a Jenkins.MANAGE permission are not saved
        assertEquals("shouldn't be allowed to change the number of executors", currentNumberExecutors, j.getInstance().getNumExecutors());
        assertEquals("shouldn't be allowed to change the shell executable", shell, getShell());
    }

    @Issue("JENKINS-60266")
    @Test
    public void globalConfigAllowedWithManagePermission() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().toEveryone());

        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        HtmlPage updated = j.submit(form);
        assertEquals("User with Jenkins.MANAGE permission should be able to update global configuration",
                     200, updated.getWebResponse().getStatusCode());
    }

    private String getShell() {
        Descriptor descriptorByName = j.getInstance().getDescriptorByName("hudson.tasks.Shell");
        return ((Shell.DescriptorImpl) descriptorByName).getShell();
    }

    // End of Moved from HusdonTest
    //-------
}
