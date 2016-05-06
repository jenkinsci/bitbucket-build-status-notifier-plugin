package org.jenkinsci.plugins.bitbucket.validator;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class BitbucketHostValidator
{
    final Set<String> supportedDomains = ImmutableSet.of("bitbucket.org", "altssh.bitbucket.org");

    public boolean isValid(final String $value)
    {
        return this.supportedDomains.contains($value);
    }

    public String renderError()
    {
        return "Bitbucket build notifier support only repositories hosted in bitbucket.org";
    }
}
