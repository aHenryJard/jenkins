package jenkins.model;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import hudson.model.labels.LabelAtom;

import static org.junit.Assert.assertEquals;

/**
 * As Jenkins.MANAGE can be enabled on startup with jenkins.permission.manage.enabled property, we need a test class
 * with this property activated.
 */
public class JenkinsManagePermissionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    static {
        // happens before the Jenkins static fields are loaded
        System.setProperty("jenkins.permission.manage.enabled", "true");
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Issue("JENKINS-60266")
    @Test
    public void configAllowedWithConfigurePermission() throws Exception {
        final String CONFIGURATOR = "configurator";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to(CONFIGURATOR));

        LabelAtom label = j.jenkins.getLabelAtom("foo");

        // it should survive the configuration roundtrip
        HtmlForm labelConfigForm = j.createWebClient().login(CONFIGURATOR).goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");
        j.submit(labelConfigForm);

        assertEquals("example description",label.getDescription());
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Issue("JENKINS-60266")
    @Test
    public void configForbiddenWithoutConfigureOrAdminPermissions() throws Exception {
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

}
