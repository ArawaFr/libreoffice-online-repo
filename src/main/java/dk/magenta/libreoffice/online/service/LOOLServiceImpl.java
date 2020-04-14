package dk.magenta.libreoffice.online.service;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LOOLServiceImpl implements LOOLService {
    private static final Log logger = LogFactory.getLog(LOOLServiceImpl.class);

    private static final long ONE_HOUR_MS = 1000 * 60 * 60;

    // TODO: Make configurable
    // 24 hours in milliseconds
    private static final long TOKEN_TTL_MS = ONE_HOUR_MS * 24;
    private static final int DEFAULT_WOPI_PORT = 9980;

    private URL wopiBaseURL;
    // In case alfresco is behind a proxy then we need the proxy's host address
    private URL alfExternalHost;
    private URL wopiDiscoveryURL;
    private WOPILoader wopiLoader;
    private NodeService nodeService;
    private SysAdminParams sysAdminParams;

    private SecureRandom random = new SecureRandom();

    /**
     * This holds a map of the the "token info(s)" mapped to a file. Each token info
     * is mapped to a user, so in essence a user may only have one token info per
     * file. <FileId, <userName, tokenInfo> >
     * <p>
     * { fileId: { <== The id of the nodeRef that refers to the file userName: WOPIAccessTokenInfo } }
     *
     *
     * fileIdAccessTokenMap is an Hazelcast IMap
     * see: https://docs.hazelcast.org/docs/2.4/javadoc/com/hazelcast/core/IMap.html
     * The get(Object key) method returns a clone of original value, modifying the returned value does not change
     * the actual value in the map. One should put modified value back to make changes visible to all nodes.
     */
    private SimpleCache<String, Map<String, WOPIAccessTokenInfo>> fileIdAccessTokenMap;

    public void setFileIdAccessTokenMap(SimpleCache<String, Map<String, WOPIAccessTokenInfo>> fileIdAccessTokenMap) {
        this.fileIdAccessTokenMap = fileIdAccessTokenMap;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setWopiBaseURL(URL wopiBaseURL) {
        this.wopiBaseURL = wopiBaseURL;
    }

    public void setWopiDiscoveryURL(URL wopiDiscoveryURL) {
        this.wopiDiscoveryURL = wopiDiscoveryURL;
    }

    public void setAlfExternalHost(URL alfExternalHost) {
        this.alfExternalHost = alfExternalHost;
    }

    /**
     * Generate and store an access token only valid for the current user/file id
     * combination.
     *
     * If an existing access token exists for the user/file id combination, then
     * extend its expiration date and return it.
     * 
     * @param fileId
     * @return
     */
    @Override
    public WOPIAccessTokenInfo createAccessToken(String fileId) {
        final String userName = AuthenticationUtil.getRunAsUser();
        final Date now = new Date();
        final Date newExpiresAt = new Date(now.getTime() + TOKEN_TTL_MS);
        Map<String, WOPIAccessTokenInfo> tokenInfoMap = this.fileIdAccessTokenMap.get(fileId);

        WOPIAccessTokenInfo tokenInfo = null;
        if (tokenInfoMap != null) {
            tokenInfo = tokenInfoMap.get(userName);
            if (tokenInfo != null) {
                if (tokenInfo.isValid() && tokenInfo.getFileId().equals(fileId)
                        && tokenInfo.getUserName().equals(userName)) {
                    // Renew token for a new time-to-live period.
                    tokenInfo.setExpiresAt(newExpiresAt);
                } else {
                    // Expired or not valid -- remove it.
                    tokenInfoMap.remove(userName);
                }
            }
        }
        if (tokenInfo == null) {
            tokenInfo = new WOPIAccessTokenInfo(generateAccessToken(), now, newExpiresAt, fileId, userName);
            if(tokenInfoMap == null) {
                tokenInfoMap = new HashMap<>();
            }
            tokenInfoMap.put(userName, tokenInfo);
        }

        // put the tokenInfoMap back to the shared cache, so other servers can see changes
        this.fileIdAccessTokenMap.put(fileId, tokenInfoMap);

        if (logger.isDebugEnabled()) {
            logger.debug("Created Access Token for user '" + userName + "' and fileId '" + fileId + "'");
        }
        return tokenInfo;
    }

    /**
     * Generates a random access token.
     * 
     * @return
     */
    @Override
    public String generateAccessToken() {
        return new BigInteger(130, random).toString(32);
    }

    /**
     * Return stored info about the given token if it exists. Otherwise, return
     * null.
     *
     * @param accessToken
     * @param fileId
     * @return
     */
    @Override
    public WOPIAccessTokenInfo getAccessToken(String accessToken, String fileId) {
        final Map<String, WOPIAccessTokenInfo> tokenInfoMap = this.fileIdAccessTokenMap.get(fileId);

        if (tokenInfoMap == null) {
            logger.warn("No token access found for " + fileId);
            return null;
        }

        WOPIAccessTokenInfo tokenInfo = null;
        // Find the token in the map values. Note that we don't know the
        // username for the token at this point, so we can't just do a
        // simple key lookup.
        for (WOPIAccessTokenInfo t : tokenInfoMap.values()) {
            if (t.getAccessToken().equals(accessToken)) {
                tokenInfo = t;
                break;
            }
        }

        if (tokenInfo == null) {
            logger.warn("Tokens exist for " + fileId + ", but not for the given access token");
        }

        // Found the access token for the given file id.
        return tokenInfo;
    }

    /**
     * Check the access token given in the request and return the nodeRef
     * corresponding to the file id passed to the request.
     *
     * Additionally, set the runAs user to the user corresponding to the token.
     *
     * @param req
     * @throws WebScriptException
     * @return
     */
    @Override
    public NodeRef checkAccessToken(WebScriptRequest req) throws WebScriptException {
        final String fileId = req.getServiceMatch().getTemplateVars().get(WOPITokenService.FILE_ID);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Check Access Token for: " + fileId);
        }
        
        if (fileId == null) {
            throw new WebScriptException("No 'fileId' parameter supplied");
        }

        final String accessToken = req.getParameter(WOPITokenService.ACCESS_TOKEN);
        final WOPIAccessTokenInfo tokenInfo = getAccessToken(accessToken, fileId);
        // Check access token
        if (accessToken == null || tokenInfo == null) {
            throw new WebScriptException(Status.STATUS_UNAUTHORIZED, "Access token invalid");
        }
        if (!tokenInfo.isValid()) {
            throw new WebScriptException(Status.STATUS_UNAUTHORIZED, "Access token expired");
        }

        AuthenticationUtil.setRunAsUser(tokenInfo.getUserName());
        return getNodeRefForFileId(fileId);
    }

    /**
     * Returns the WOPI src URL for a given nodeRef and action.
     *
     * @param nodeRef
     * @param action
     * @return
     */
    @Override
    public String getWopiSrcURL(NodeRef nodeRef, String action) throws IOException {
        final ContentData contentData = (ContentData) nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        return wopiLoader.getSrcURL(contentData.getMimetype(), action);
    }

    /**
     * Returns the id component of a NodeRef
     * 
     * @param nodeRef
     * @return
     */
    @Override
    public String getFileIdForNodeRef(NodeRef nodeRef) {
        return nodeRef.getId();
    }

    /**
     * Returns a NodeRef given a file Id. Note: Checks to see if the node exists
     * aren't performed
     * 
     * @param fileId
     * @return
     */
    @Override
    public NodeRef getNodeRefForFileId(String fileId) {
        return new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, fileId);
    }

    /**
     * In the case that Alfresco is behind a proxy and not using the proxy hostname
     * in the alfresco config section of the alfresco-global.properties file, then
     * we should be able to set a property in alfresco-global.properties for this
     * service to use.
     *
     * @return
     */
    @Override
    public String getAlfrescoProxyDomain() {
        return alfExternalHost.getHost();
    }

    @Override
    public URL getAlfExternalHost() {
        return alfExternalHost;
    }

    public void setSysAdminParams(SysAdminParams sysAdminParams) {
        this.sysAdminParams = sysAdminParams;
    }

    public void init() {
        if (wopiBaseURL == null) {
            try {
                logger.warn("The wopiBaseURL param wasn't found in alfresco-global.properties."
                        + "Assuming lool service is on the same host and setting url to match.");
                wopiBaseURL = new URL("https", sysAdminParams.getAlfrescoHost(), DEFAULT_WOPI_PORT, "/");
            } catch (MalformedURLException e) {
                throw new AlfrescoRuntimeException("Invalid WOPI Base URL: " + this.wopiBaseURL, e);
            }
        }

        // We should actually never throw an exception here unless of course.......
        if (wopiDiscoveryURL == null) {
            try {
                wopiDiscoveryURL = new URL(
                        wopiBaseURL.getProtocol() + wopiBaseURL.getHost() + wopiBaseURL.getPort() + "/discovery");
                logger.warn("The wopiDiscoveryURL param wasn't found in alfresco-global.properties. "
                        + "\nWe will assume that the discovery.xml file is hosted on this"
                        + "server and construct a url path based on this: " + wopiDiscoveryURL.toString());
            } catch (MalformedURLException mue) {
                logger.error("Unable to create discovery URL. (Should never be thrown so this is an "
                        + "interesting situation we find ourselves.. To the bat cave Robin!!)");
                throw new AlfrescoRuntimeException("Invalid WOPI Base URL: " + this.wopiBaseURL, mue);
            }
        }

        wopiLoader = new WOPILoader(wopiDiscoveryURL);
    }

    public class WOPILoader {
        private Document discoveryDoc;
        private URL wopiDiscoveryURL;

        public WOPILoader(URL wopiDiscoveryURL) {
            this.wopiDiscoveryURL = wopiDiscoveryURL;
        }

        /**
         * Return the srcurl for a given mimetype.
         *
         * @param mimeType
         * @return
         */
        public String getSrcURL(String mimeType, String action) throws IOException {
            // Attempt to reload discovery.xml from host if it isn't already
            // loaded.
            if (this.discoveryDoc == null) {
                try {
                    loadDiscoveryXML();
                } catch (IOException e) {
                    logger.error("Failed to fetch discovery.xml file from server (" + wopiDiscoveryURL.toString() + ")",
                            e);
                    throw e;
                }
            }

            final XPathFactory xPathFactory = XPathFactory.newInstance();
            final XPath xPath = xPathFactory.newXPath();
            final String xPathExpr = ("/wopi-discovery/net-zone/app[@name='${mimeType}']/action[@name='${action}']/@urlsrc")
                    .replace("${mimeType}", mimeType).replace("${action}", action);
            try {
                return xPath.evaluate(xPathExpr, this.discoveryDoc);
            } catch (XPathExpressionException e) {
                logger.error("XPath Error return null", e);
            }
            return null;
        }

        private void loadDiscoveryXML() throws IOException {
            this.discoveryDoc = parse(fetchDiscoveryXML());
        }

        /**
         * Parse a discovery.xml file input stream.
         *
         * @param discoveryInputStream
         * @return
         */
        private Document parse(InputStream discoveryInputStream) {
            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            try {
                final DocumentBuilder builder = builderFactory.newDocumentBuilder();
                return builder.parse(discoveryInputStream);
            } catch (ParserConfigurationException | IOException | SAXException e) {
                logger.error("Parse Error return null", e);
            }
            return null;
        }

        private InputStream fetchDiscoveryXML() throws IOException {
            HttpURLConnection connection = (HttpURLConnection) this.wopiDiscoveryURL.openConnection();

            if (logger.isDebugEnabled()) {
                logger.debug("Http connection for discovery xml returned with a [" + connection.getResponseCode()
                        + "] response code.");
            }

            try {
                final byte[] conn = IOUtils.toByteArray(connection.getInputStream());
                return new ByteArrayInputStream(conn);
            } catch (IOException e) {
                logger.error("There was an error fetching discovery.xml", e);
            }
            return null;

        }
    }
}
