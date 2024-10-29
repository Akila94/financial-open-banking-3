/**
 * Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.wso2.openbanking.accelerator.common.model;

/**
 * PSD2 role enum class.
 */
public enum PSD2RoleEnum {

    AISP("aisp"), PISP("pisp"), CBPII("cbpii"), ASPSP("aspsp"), PSP_AI("psp_ai"),
    PSP_PI("psp_pi"), PSP_IC("psp_ic"), PSP_AS("psp_as");

    private String value;

    PSD2RoleEnum(String value) {

        this.value = value;
    }

    public String toString() {

        return value;
    }

    public static PSD2RoleEnum fromValue(String text) {

        for (PSD2RoleEnum apiTypeEnum : PSD2RoleEnum.values()) {
            if (String.valueOf(apiTypeEnum.value).equalsIgnoreCase(text)) {
                return apiTypeEnum;
            }
        }
        return null;
    }
}
