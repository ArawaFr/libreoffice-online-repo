package dk.magenta.libreoffice.online.service;

import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import java.io.IOException;
import java.net.URL;

/**
 * Created by seth on 30/04/16.
 */
public interface LOOLService {
    /**
     * Generate and store an access token only valid for the current user/file id
     * combination.
     *
     * If an existing access token exists for the user/file id combination, then
     * extend its expiration date and return it.
     * 
     * @param nodeRef
     * @return
     */
    WOPIAccessTokenInfo createAccessToken(NodeRef nodeRef);

    /**
     * Return stored info about the given token if it exists. Otherwise, return
     * null.
     *
     * @param accessToken
     * @param fileId
     * @return
     */
    WOPIAccessTokenInfo getAccessToken(String accessToken, String fileId);

    /**
     * Check the access token given in the request and return the WOPIAccessTokenInfo
     * corresponding to the token
     *
     *
     * @param req
     * @throws WebScriptException
     * @return
     */
    WOPIAccessTokenInfo checkAccessToken(WebScriptRequest req) throws WebScriptException;

    /**
     * Returns the WOPI src URL for a given nodeRef and action.
     *
     * @param nodeRef
     * @param action
     * @return
     * @throws IOException
     */
    String getWopiSrcURL(NodeRef nodeRef, String action) throws IOException;

    /**
     * Returns a NodeRef given a file Id. Note: Checks to see if the node exists
     * aren't performed
     * 
     * @param fileId
     * @return
     */
    NodeRef getNodeRefForFileId(String fileId);

    /**
     * In the case that Alfresco is behind a proxy and not using the proxy hostname
     * in the alfresco config section of the alfresco-global.properties file, then
     * we should be able to set a property in alfresco-global.properties for this
     * service to use.
     * 
     * @return
     */
    String getAlfrescoProxyDomain();

    /**
     * PostMessageOrigin need full URI.
     * 
     * @return
     */
    URL getAlfExternalHost();
}
