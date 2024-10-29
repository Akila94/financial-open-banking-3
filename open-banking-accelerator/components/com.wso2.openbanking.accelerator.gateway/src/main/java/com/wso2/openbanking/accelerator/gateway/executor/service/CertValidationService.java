/*
 * Copyright (c) 2021-2023, WSO2 LLC. (https://www.wso2.com). All Rights Reserved.
 *
 * This software is the property of WSO2 LLC. and its suppliers, if any.
 * Dissemination of any information or reproduction of any material contained
 * herein in any form is strictly forbidden, unless permitted by WSO2 expressly.
 * You may not alter or remove any copyright or other notice from copies of this content.
 */

package com.wso2.openbanking.accelerator.gateway.executor.service;

import com.wso2.openbanking.accelerator.common.config.OpenBankingConfigParser;
import com.wso2.openbanking.accelerator.common.exception.CertificateValidationException;
import com.wso2.openbanking.accelerator.common.exception.TPPValidationException;
import com.wso2.openbanking.accelerator.common.model.PSD2RoleEnum;
import com.wso2.openbanking.accelerator.common.util.eidas.certificate.extractor.CertificateContent;
import com.wso2.openbanking.accelerator.common.util.eidas.certificate.extractor.CertificateContentExtractor;
import com.wso2.openbanking.accelerator.gateway.cache.GatewayCacheKey;
import com.wso2.openbanking.accelerator.gateway.cache.TppValidationCache;
import com.wso2.openbanking.accelerator.gateway.executor.model.RevocationStatus;
import com.wso2.openbanking.accelerator.gateway.executor.revocation.RevocationValidator;
import com.wso2.openbanking.accelerator.gateway.internal.TPPCertValidatorDataHolder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CertRevocationValidation Service class is responsible for validating client certificates.
 */
public class CertValidationService {

    private static final Log log = LogFactory.getLog(CertValidationService.class);
    private static CertValidationService certValidationService;

    private CertValidationService() {

    }

    /**
     * Singleton getInstance method to create only one object.
     *
     * @return CertValidationService object
     */
    public static synchronized CertValidationService getInstance() {
        if (certValidationService == null) {
            certValidationService = new CertValidationService();
        }

        return certValidationService;
    }


    /**
     * Validate the certificate revocation status.
     *
     * @param peerCertificate   X509Certificate
     * @param issuerCertificate X509Certificate
     * @return true if the certificate is not revoked
     * @deprecated use {@link #verify(X509Certificate, X509Certificate, int, int, int, int)}  instead
     */
    @Deprecated
    public boolean verify(X509Certificate peerCertificate, X509Certificate issuerCertificate, int retryCount) {

        OpenBankingConfigParser openBankingConfigParser = OpenBankingConfigParser.getInstance();
        RevocationValidatorFactory revocationValidatorFactory = new RevocationValidatorFactory();
        Map<Integer, String> revocationValidators = openBankingConfigParser.getCertificateRevocationValidators();

        // OCSP validation is checked first as it is faster than the CRL validation. Moving to CRL validation
        // only if an error occurs during the OCSP validation.
        RevocationValidator[] validators = revocationValidators
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .map(type -> revocationValidatorFactory.getValidator(type, retryCount))
                .filter(Objects::nonNull)
                .toArray(RevocationValidator[]::new);

        for (RevocationValidator validator : validators) {
            RevocationStatus revocationStatus = isRevoked(validator, peerCertificate, issuerCertificate);
            if (RevocationStatus.GOOD == revocationStatus) {
                return true;
            } else if (RevocationStatus.REVOKED == revocationStatus) {
                return false;
            }
        }
        log.error("Unable to verify certificate revocation information");
        return false;
    }

    /**
     * Validate the certificate revocation status.
     *
     * @param peerCertificate        X509Certificate
     * @param issuerCertificate      X509Certificate
     * @param retryCount             retry count
     * @param connectTimeout         connect timeout
     * @param connectionRequestTimeout connection request timeout
     * @param socketTimeout          socket timeout
     * @return true if the certificate is not revoked
     */
    public boolean verify(X509Certificate peerCertificate, X509Certificate issuerCertificate, int retryCount,
                          int connectTimeout, int connectionRequestTimeout, int socketTimeout) {

        OpenBankingConfigParser openBankingConfigParser = OpenBankingConfigParser.getInstance();
        RevocationValidatorFactory revocationValidatorFactory = new RevocationValidatorFactory();
        Map<Integer, String> revocationValidators = openBankingConfigParser.getCertificateRevocationValidators();

        // OCSP validation is checked first as it is faster than the CRL validation. Moving to CRL validation
        // only if an error occurs during the OCSP validation.
        RevocationValidator[] validators = revocationValidators
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .map(type -> revocationValidatorFactory.getValidator(type, retryCount, connectTimeout,
                        connectionRequestTimeout, socketTimeout))
                .filter(Objects::nonNull)
                .toArray(RevocationValidator[]::new);

        for (RevocationValidator validator : validators) {
            RevocationStatus revocationStatus = isRevoked(validator, peerCertificate, issuerCertificate);
            if (RevocationStatus.GOOD == revocationStatus) {
                return true;
            } else if (RevocationStatus.REVOKED == revocationStatus) {
                return false;
            }
        }
        log.error("Unable to verify certificate revocation information");
        return false;
    }

    private RevocationStatus isRevoked(RevocationValidator validator, X509Certificate peerCertificate,
                                       X509Certificate issuerCertificate) {

        if (log.isDebugEnabled()) {
            log.debug("X509 Certificate validation with " + validator.getClass().getSimpleName());
        }
        try {
            return validator.checkRevocationStatus(peerCertificate, issuerCertificate);
        } catch (CertificateValidationException e) {
            log.warn("Unable to validate certificate revocation with " +
                    validator.getClass().getSimpleName(), e);
            return RevocationStatus.UNKNOWN;
        }
    }

    /**
     * Validates TPP roles with the required roles and the roles inside the certificate.
     *
     * @param tppCertificate TPP certificate
     * @param requiredPSD2Roles required roles
     * @return true is validation is successful, false otherwise
     * @throws TPPValidationException TPP validation exception
     * @throws CertificateValidationException cert validation exception
     */
    public boolean validateTppRoles(X509Certificate tppCertificate, List<PSD2RoleEnum> requiredPSD2Roles)
            throws TPPValidationException, CertificateValidationException {

        if (TPPCertValidatorDataHolder.getInstance().isTppValidationEnabled()) {

            String tppValidationServiceImplClassPath =
                    TPPCertValidatorDataHolder.getInstance().getTPPValidationServiceImpl();
            if (StringUtils.isNotBlank(tppValidationServiceImplClassPath)) {
                TPPValidationService tppValidationService =
                        TPPCertValidatorDataHolder.getInstance().getTppValidationService();

                if (tppValidationService != null) {
                    return validateTPP(tppValidationService, tppCertificate, requiredPSD2Roles);
                } else {
                    throw new TPPValidationException(
                            "Unable to find the implementation class for TPP validation service");
                }
            } else {
                throw new TPPValidationException("TPP validation service " +
                        "class implementation is empty");
            }
        } else if (TPPCertValidatorDataHolder.getInstance().isPsd2RoleValidationEnabled()) {
            return isRequiredRolesMatchWithScopes(tppCertificate, requiredPSD2Roles);
        } else {
            throw new TPPValidationException("Both TPP validation and PSD2 role validation services are disabled");
        }
    }


    /**
     * Validates TPP roles with the required roles and the roles inside the certificate.
     *
     * @param tppCertificate TPP certificate
     * @param requiredPSD2Roles required roles
     * @param certRoles roles of the certificate
     * @return true if the validation is successful, false otherwise
     * @throws TPPValidationException TPP validation exception
     * @throws CertificateValidationException cert validation exception
     */
    public boolean validateTppRoles(X509Certificate tppCertificate, List<PSD2RoleEnum> requiredPSD2Roles,
                                    List<String> certRoles)
            throws TPPValidationException, CertificateValidationException {

        if (TPPCertValidatorDataHolder.getInstance().isTppValidationEnabled()) {

            String tppValidationServiceImplClassPath =
                    TPPCertValidatorDataHolder.getInstance().getTPPValidationServiceImpl();
            if (StringUtils.isNotBlank(tppValidationServiceImplClassPath)) {
                TPPValidationService tppValidationService =
                        TPPCertValidatorDataHolder.getInstance().getTppValidationService();

                if (tppValidationService != null) {
                    return validateTPP(tppValidationService, tppCertificate, requiredPSD2Roles);
                } else {
                    throw new TPPValidationException(
                            "Unable to find the implementation class for TPP validation service");
                }
            } else {
                throw new TPPValidationException("TPP validation service " +
                        "class implementation is empty");
            }
        } else if (TPPCertValidatorDataHolder.getInstance().isPsd2RoleValidationEnabled()) {
            return isRequiredRolesMatchWithScopes(requiredPSD2Roles, certRoles);
        } else {
            throw new TPPValidationException("Both TPP validation and PSD2 role validation services are disabled");
        }
    }

    /**
     * Validate whether the psd2 roles match with the scopes
     *
     * @param tppCertificate    eidas certificate with roles
     * @param requiredPSD2Roles client requested roles
     * @return true if all required values are present in the certificate
     */
    private boolean isRequiredRolesMatchWithScopes(X509Certificate tppCertificate,
                                                   List<PSD2RoleEnum> requiredPSD2Roles)
            throws CertificateValidationException, TPPValidationException {


        // Extract the certContent from the eidas certificate (i.e. roles, authorization number, etc)
        CertificateContent certContent = CertificateContentExtractor.extract(tppCertificate);

        if (log.isDebugEnabled()) {
            log.debug("The TPP is requesting roles: " + requiredPSD2Roles);
            log.debug("Provided PSD2 eIDAS certificate" +
                    " contains the role: " + certContent.getPspRoles());
        }

        // Validate whether the eIDAS certificate contains the required roles that matches with the token scopes.
        for (PSD2RoleEnum requiredRole : requiredPSD2Roles) {
            if (!certContent.getPspRoles().contains(requiredRole.name())) {
                // Return false if any one of the roles that are bound to the scope is not present in the PSD2
                // role list of the client eIDAS certificate.
                final String errorMsg = "The PSD2 eIDAS certificate does not contain the required role "
                        + requiredRole.toString();

                log.error(errorMsg);
                throw new TPPValidationException(errorMsg);
            }
        }
        return true;
    }


    /**
     * Validate whether the psd2 roles match with the scopes
     *
     * @param requiredPSD2Roles client requested roles
     * @return true if all required values are present in the certificate
     */
    private boolean isRequiredRolesMatchWithScopes(List<PSD2RoleEnum> requiredPSD2Roles, List<String> certRoles)
            throws TPPValidationException {


        if (log.isDebugEnabled()) {
            log.debug("The TPP is requesting roles: " + requiredPSD2Roles);
            log.debug("Provided PSD2 eIDAS certificate" +
                    " contains the role: " + certRoles);
        }

        // Validate whether the eIDAS certificate contains the required roles that matches with the token scopes.
        for (PSD2RoleEnum requiredRole : requiredPSD2Roles) {
            if (!certRoles.contains(requiredRole.name())) {
                // Return false if any one of the roles that are bound to the scope is not present in the PSD2
                // role list of the client eIDAS certificate.
                final String errorMsg = "The PSD2 eIDAS certificate does not contain the required role "
                        + requiredRole.toString();

                log.error(errorMsg);
                throw new TPPValidationException(errorMsg);
            }
        }
        return true;
    }

    /**
     * Validates the TPP using a custom class implementation which implements TPPValidationService interface.
     *
     * @param tppValidationService custom TPP validation service
     * @param tppCertificate TPP certificate
     * @param requiredPSD2Roles required TPP roles
     * @return ture if validation is successful, false otherwise
     * @throws TPPValidationException TPP validation exception
     */
    private boolean validateTPP(TPPValidationService tppValidationService, X509Certificate tppCertificate,
                                List<PSD2RoleEnum> requiredPSD2Roles) throws TPPValidationException {

        // Initializing certificate cache and cache key
        TppValidationCache tppValidationServiceCache = TppValidationCache.getInstance();
        String tppValidationCacheKeyStr = tppValidationService.getCacheKey(tppCertificate,
                requiredPSD2Roles, Collections.emptyMap());
        GatewayCacheKey tppValidationCacheKey = GatewayCacheKey.of(tppValidationCacheKeyStr);

        // Executing TPP role validation process or retrieve last status from cache
        if (tppValidationServiceCache.getFromCache(tppValidationCacheKey) != null) {
            // previous result is present in cache, return result
            return tppValidationServiceCache.getFromCache(tppValidationCacheKey);
        } else {
            final boolean result = tppValidationService.validate(tppCertificate, requiredPSD2Roles,
                    Collections.emptyMap());
            if (result) {
                // Adding result to cache
                tppValidationServiceCache.addToCache(tppValidationCacheKey, true);
                return true;
            }
        }
        return false;
    }
}
