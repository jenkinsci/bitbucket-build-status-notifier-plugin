# Bitbucket build status notifier plugin for Jenkins - [![Build Status][jenkins-status]][jenkins-builds]

Every time you trigger a build, you don't have to log in to your build server to see if it passed or failed. Now
you will be able to know when your build is passing right within the Bitbucket UI.

## Features

* Notify to Bitbucket for the following build events:
 * Build start
 * Build finish

## Dependencies
This plugin depends on other Jenkins plugin the [Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin).
Please install it before if it's still not installed on your Jenkins server.

## Instructions

First you need to get a OAuth consumer key/secret from Bitbucket.

1. Login into your Bitbucket account.
2. Click your account name and then in **Settings** from the menu bar.
3. Click **OAuth** from the menu bar.
4. Press the **Add consumer** button.
6. The system requests the following information:
 1. Give a representative **name** to the consumer e.g. Jenkins build status notifier.
 2. Although is not used, a **Callback URL** must be set e.g. ci.your-domain.com.
 2. Leave blank the **URL** field.
 3. Add **Read** and **Write** permissions to **Repositories**.
 4. Click **Save** button and a **Key** and **Secret** will be automatically generated.

Second, you need to configure Jenkins:

1. Open Jenkins **Configure System** page.
2. Set correct URL to **Jenkins URL**.
3. Go to the Job you want notifies the builds to Bitbucket.
4. Click **Configure**.
5. Click **Add post-build action**.
6. Set the **Key** and **Secret** you previously created.
7. Choose whether you want to notify the build status on Jenkins to Bitbucket.

## Contributions

Contributions are welcome! For feature requests and bug reports please [submit an issue].

## License

The MIT License (MIT)

Copyright (c) 2015 Flagbit GmbH & Co. KG

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of
the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

[jenkins-builds]: https://jenkins.ci.cloudbees.com/job/plugins/job/bitbucket-build-status-notifier-plugin/
[jenkins-status]: https://jenkins.ci.cloudbees.com/buildStatus/icon?job=plugins/bitbucket-build-status-notifier-plugin
