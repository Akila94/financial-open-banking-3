/**
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.financial.services.accelerator.event.notifications.service.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.financial.services.accelerator.common.exception.ConsentManagementException;
import org.wso2.financial.services.accelerator.consent.mgt.dao.models.ConsentResource;
import org.wso2.financial.services.accelerator.consent.mgt.service.impl.ConsentCoreServiceImpl;
import org.wso2.financial.services.accelerator.event.notifications.service.constants.EventNotificationConstants;
import org.wso2.financial.services.accelerator.event.notifications.service.dto.NotificationCreationDTO;
import org.wso2.financial.services.accelerator.event.notifications.service.exception.FSEventNotificationException;
import org.wso2.financial.services.accelerator.event.notifications.service.model.EventCreationResponse;
import org.wso2.financial.services.accelerator.event.notifications.service.service.EventCreationService;
import org.wso2.financial.services.accelerator.event.notifications.service.util.EventNotificationServiceUtil;

/**
 * This is to handle FS Event Creation.
 */
public class DefaultEventCreationServiceHandler implements EventCreationServiceHandler {

    private static final Log log = LogFactory.getLog(DefaultEventCreationServiceHandler.class);
    private EventCreationService eventCreationService = new EventCreationService();

    public void setEventCreationService(EventCreationService eventCreationService) {
        this.eventCreationService = eventCreationService;
    }

    /**
     * This method is used to publish FS events in the accelerator database.
     *
     * @param notificationCreationDTO  Notification details DTO
     * @return EventCreationResponse   Response after event creation
     */
    public EventCreationResponse publishEvent(NotificationCreationDTO notificationCreationDTO) {

        //validate if the resourceID is existing
        ConsentResource consentResource = null;
        ConsentCoreServiceImpl consentCoreService = EventNotificationServiceUtil.getConsentCoreServiceImpl();
        EventCreationResponse eventCreationResponse = new EventCreationResponse();

        try {
            consentResource = consentCoreService.getConsent(notificationCreationDTO.getResourceId(),
                    false);

            if (log.isDebugEnabled()) {
                log.debug("Consent resource available for resource ID " +
                        consentResource.getConsentID().replaceAll("[\r\n]", ""));
            }
        } catch (ConsentManagementException e) {
            log.error("Consent Management Exception when validating the consent resource", e);
            eventCreationResponse.setErrorResponse(String.format("A resource was not found for the resource " +
                            "id : '%s' in the database. ", notificationCreationDTO.getResourceId()));
            eventCreationResponse.setStatus(EventNotificationConstants.BAD_REQUEST);
            return eventCreationResponse;
        }

        //validate if the clientID is existing
        try {
            EventNotificationServiceUtil.validateClientId(notificationCreationDTO.getClientId());

        } catch (FSEventNotificationException e) {
            log.error("Invalid client ID", e);
            eventCreationResponse.setErrorResponse(String.format("A client was not found" +
                            " for the client id : '%s' in the database. ",
                    notificationCreationDTO.getClientId().replaceAll("[\r\n]", "")));
            eventCreationResponse.setStatus(EventNotificationConstants.BAD_REQUEST);
            return eventCreationResponse;
        }

        String registrationResponse = "";
        try {
            registrationResponse = eventCreationService.publishEventNotification(notificationCreationDTO);
            JSONObject responseJSON = new JSONObject();
            responseJSON.put(EventNotificationConstants.NOTIFICATIONS_ID, registrationResponse);
            eventCreationResponse.setStatus(EventNotificationConstants.CREATED);
            eventCreationResponse.setResponseBody(responseJSON);
            return eventCreationResponse;

        } catch (FSEventNotificationException e) {
            log.error("FS Event Notification Creation error", e);
        }

        eventCreationResponse.setStatus(EventNotificationConstants.BAD_REQUEST);
        eventCreationResponse.setErrorResponse("Error in event creation request payload");
        return eventCreationResponse;
    }
}
