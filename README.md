# Bitbucket Cloud build status notifier plugin for Jenkins - [![Build Status][jenkins-status]][jenkins-builds]

Every time you trigger a build, you don't have to log in to your build server to see if it passed or failed. Now
you will be able to know when your build is passing right within the Bitbucket Cloud UI.

Note: This plugin aims at the Atlassian-hosted BitBucket Cloud solution, not BitBucket Server (formerly known as Stash).

## Features

* Notify to Bitbucket for the following build events:
 * Build start
 * Build finish

## Dependencies
This plugin depends on other Jenkins plugins:

* [Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)
* [Mercurial Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Mercurial+Plugin)
* [Credentials Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin)

Please install them before if they are still not installed on your Jenkins server.

## Instructions

### Create a OAuth Consumer
First you need to get a OAuth consumer key/secret from Bitbucket.

1. Login into your Bitbucket account.
2. Click your account name and then in **Settings** from the menu bar.
3. Click **OAuth** from the menu bar.
4. Press the **Add consumer** button.
6. The system requests the following information:
 1. Give a representative **name** to the consumer e.g. Jenkins build status notifier.
 2. Although is not used, a **Callback URL** must be set e.g. ci.your-domain.com.
 2. Leave blank the **URL** field.
 3. Enable **This is a private consumer**.
 4. Add **Read** and **Write** permissions to **Repositories**.
 5. Click **Save** button and a **Key** and **Secret** will be automatically generated.

### Ensure Jenkins URL is set
Second, ensure that Jenkins URL is properly set:

1. Open Jenkins **Manage Jenkins** page.
2. Click **Configure System** page.
3. Go to the section **Jenkins Location**.
4. Set correct URL to **Jenkins URL**.
5. Click **Save** button.

### Add OAuth Credentials to Jenkins
Third, you need to add the Bitbucket OAuth Consumer credentials. You have two ways to configure it globally or locally:

#### Global

1. Open Jenkins **Manage Jenkins** page.
2. Click **Configure System**.
3. Go to the section **Bitbucket Build Status Notifier plugin**
4. If you still don't have stored the credentials click **Add**, otherwise you can skip this step.
 1. Select **Username with password**.
 2. Set the the OAuth consumer **key** in **Username**.
 3. Set the the OAuth consumer **secret** in **Password**.
 4. Click **Add** button.
5. Select the desired credentials.
6. Click **Save** button.

#### Local

1. Go to the Job you want notifies the builds to Bitbucket.
2. Click **Configure**.
3. Click **Add post-build action**.
4. Select **Bitbucket notify build status**.
5. Click **Advanced** button.
6. If you still don't have stored the credentials click **Add**, otherwise you can skip this step.
 1. Select **Username with password**.
 2. Set the the OAuth consumer **key** in **Username**.
 3. Set the the OAuth consumer **secret** in **Password**.
 4. Click **Add** button.
7. Select the desired credentials.

### Configure Jenkins to notify Bitbucket from a standard build

Once you have configured the credentials, configure Jenkins to notify Bitbucket.

1. Go to the Job you want notifies the builds to Bitbucket.
2. Click **Configure**.
3. Select **Bitbucket notify build status**.
4. Choose whether you want to notify the build status on Jenkins to Bitbucket.
5. Choose whether given a same scm revision you want to notify a new build status for every job build or to override the latest one.

### Pipeline step to notify Bitbucket

Once you have configured the credential, you can notify BitBucket from your Pipeline script through the `bitbucketStatusNotify` step.

#### Usage

The `bitbucketStatusNotify` step notifies the status of a build identified by a build key and build name to BitBucket.
If `buildKey` and `buildName` parameters are not provided, a standard name will be assigned to the build (NameOfYourJob #numberOfBuild - eg. MyProject #32).

```groovy
  ...
  stage 'Build'
    bitbucketStatusNotify(
      buildState: 'INPROGRESS',
      buildKey: 'build',
      buildName: 'Build',
      repoSlug: 'my-awesome-project',
      commitId: 'a83c709e9d514421ef614ef0a1117366c84c6304'      
    )
    try {
        myBuildFunction()
        bitbucketStatusNotify(
          buildState: 'SUCCESSFUL',
          buildKey: 'build',
          buildName: 'Build',
          repoSlug: 'my-awesome-project',
          commitId: 'a83c709e9d514421ef614ef0a1117366c84c6304'          
        )
    } catch(Exception e) {
        bitbucketStatusNotify(
          buildState: 'FAILED',
          buildKey: 'build',
          buildName: 'Build',
          buildDescription: 'Something went wrong with build!',
          repoSlug: 'my-awesome-project',
          commitId: 'a83c709e9d514421ef614ef0a1117366c84c6304'      
        )
    }
  stage 'Test'
    bitbucketStatusNotify(
      buildState: 'INPROGRESS',
      buildKey: 'test',
      buildName: 'Test',
      repoSlug: 'my-awesome-project',
      commitId: 'a83c709e9d514421ef614ef0a1117366c84c6304'
    )
    try {
        myTestFunction()
        bitbucketStatusNotify(
          buildState: 'SUCCESSFUL',
          buildKey: 'test',
          buildName: 'Test',
          repoSlug: 'my-awesome-project',
          commitId: 'a83c709e9d514421ef614ef0a1117366c84c6304'
        )
    } catch(Exception e) {
        bitbucketStatusNotify(
          buildState: 'FAILED',
          buildKey: 'test',
          buildName: 'Test',
          buildDescription: 'Something went wrong with tests!',
          repoSlug: 'my-awesome-project',
          commitId: 'a83c709e9d514421ef614ef0a1117366c84c6304'
        )
    }
  ...
```

#### API Summary

Parameter:

| Name | Type | Optional | Description |
| --- | --- | --- | --- |
| `buildState` | `INPROGRESS`,`SUCCESSFUL`,`FAILED` | no | The status of the current build phase
| `buildKey` | String | yes | The unique key identifying the current build phase
| `buildName` | String | yes | The build phase's name shown on BitBucket
| `buildDescription` | String | yes | The build phase's description shown on BitBucket
| `repoSlug`| String | yes | The slug of the bitbucket repository to send the notification to
| `commitId` | String | yes | The id of the commit to attach the status notification to 

Note that the `repoSlug` and `commitId` parameters work only when they are both specified.

## Contributions

Contributions are welcome! For feature requests and bug reports please read the following Wiki page for guidelines on [how to submit an issue][how-to-submit-issue].

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

[jenkins-builds]: https://ci.jenkins.io/job/plugins/job/bitbucket-build-status-notifier-plugin/job/master
[jenkins-status]: https://ci.jenkins.io/buildStatus/icon?job=plugins/bitbucket-build-status-notifier-plugin/master
[how-to-submit-issue]: https://wiki.jenkins-ci.org/display/JENKINS/How+to+report+an+issue
