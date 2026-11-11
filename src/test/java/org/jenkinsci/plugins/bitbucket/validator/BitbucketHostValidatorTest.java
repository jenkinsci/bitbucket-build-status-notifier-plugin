package org.jenkinsci.plugins.bitbucket.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BitbucketHostValidatorTest {

    private final BitbucketHostValidator validator = new BitbucketHostValidator();

    @Test
    public void acceptsLegacyBitbucketHost() {
        assertTrue(validator.isValid("bitbucket.org"));
    }

    @Test
    public void acceptsAltSshHost() {
        assertTrue(validator.isValid("altssh.bitbucket.org"));
    }

    @Test
    public void acceptsNewSshHost() {
        assertTrue(validator.isValid("ssh.bitbucket.org"));
    }

    @Test
    public void rejectsNonBitbucketHost() {
        assertFalse(validator.isValid("github.com"));
    }
}
