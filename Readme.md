LibreOffice Online Alfresco Repository AMP module
========

This is an Alfresco Repository AMP module that adds WOPI host capabilities to Alfresco.<br/>
It does not implement the full WOPI protocol, merely the following endpoint interfaces:
- [ ] CheckFileInfo
- [ ] GetFile and
- [ ] PutFile

This module was developed against Alfresco version 5.2.g.

### Webapplication Open Platform Interface protocols

For information on WOPI, see http://wopi.readthedocs.io/projects/wopirest/en/latest/endpoints.html for the list of WOPI endpoints.

### Installing

First install LibreOfficeOnLine.
(See https://github.com/LibreOffice/online for the latest installation instructions).

See http://docs.alfresco.com/5.0/tasks/dev-extensions-tutorials-simple-module-install-amp.html for how to install this module in your Alfresco repository installation.

Add the following properties to your alfresco-global.properties file:

- [ ] lool.wopi.url=https://lool.server.domain:[port-number]
- [ ] lool.wopi.url.discovery=https://host.domain/path-to-discovery.xml
- [ ] lool.wopi.alfresco.host=https://alfresco.proxy-url-host.domain (Not important but used to get around proxy issues for now)

### JDK 8+

The page module evaluator requires JDK higher than or equal to version 8.

Go to http://www.oracle.com/technetwork/java/javase/downloads/index.html and click on button "Download JDK".

There are installation instructions on that page as well. To verify that your installation was successful, run "java -version" on the command line.
That should print the installed version of your JDK.

### Contributing

Submit pull a pull request. You're also welcome to fork the code for your own purpose(s).

### License

This code is released and distributed under the Mozilla Public License 2.0:

- [ ] https://opensource.org/licenses/MPL-2.0
- [ ] https://tldrlegal.com/license/mozilla-public-license-2.0-%28mpl-2%29

### Reporting problems

Every self-respecting developer should have read link on how to ask smart questions: http://www.catb.org/~esr/faqs/smart-questions.html.

After you've done that you can create issues in https://github.com/magenta-aps/libreoffice-online-repo/issues.

### Changelog

**version 1.2.2**

* Add log to checkAccessToken

**version 1.2.1**

* Fix bug that save empty version

**version 1.2**

* Add implentation of X-LOOL-WOPI-Timestamp

**version 1.1**

* Add compatibility with clustered environments
* update to alfresco-sdk 2.2.0

**version 1.0**

* Create lool:collaboraOnline aspect
* webscript to add and remove lool:collaboraOnline aspect
* Create lool:autosave property 
* webscript to clean history version

### Features to consider

- [ ] Support Save-As (TemplateSaveAs)
- [ ] WOPI: added FollowUser_Changed notification
- [ ] Support X-LOOL-WOPI-IsExitSave (libreoffice-6.2.0.1)
- [ ] WOPI: Extend HideUserList to CheckFileInfo
- [ ] insert remote image
- [ ] file share functionality
- [ ] document signing

