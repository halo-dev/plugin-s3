/* tslint:disable */
/* eslint-disable */
/**
 * Halo
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 2.18.0-SNAPSHOT
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


import type { Configuration } from '../configuration';
import type { AxiosPromise, AxiosInstance, RawAxiosRequestConfig } from 'axios';
import globalAxios from 'axios';
// Some imports not used depending on template conditions
// @ts-ignore
import { DUMMY_BASE_URL, assertParamExists, setApiKeyToObject, setBasicAuthToObject, setBearerAuthToObject, setOAuthToObject, setSearchParams, serializeDataIfNeeded, toPathString, createRequestFunction } from '../common';
// @ts-ignore
import { BASE_PATH, COLLECTION_FORMATS, type RequestArgs, BaseAPI, RequiredError, operationServerMap } from '../base';
// @ts-ignore
import type { S3OsProperties } from '../models';
/**
 * PolicyConfigValidationControllerApi - axios parameter creator
 * @export
 */
export const PolicyConfigValidationControllerApiAxiosParamCreator = function (configuration?: Configuration) {
    return {
        /**
         * 
         * @param {S3OsProperties} s3OsProperties 
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        validatePolicyConfig: async (s3OsProperties: S3OsProperties, options: RawAxiosRequestConfig = {}): Promise<RequestArgs> => {
            // verify required parameter 's3OsProperties' is not null or undefined
            assertParamExists('validatePolicyConfig', 's3OsProperties', s3OsProperties)
            const localVarPath = `/apis/s3os.halo.run/v1alpha1/policies/s3/validation`;
            // use dummy base URL string because the URL constructor only accepts absolute URLs.
            const localVarUrlObj = new URL(localVarPath, DUMMY_BASE_URL);
            let baseOptions;
            if (configuration) {
                baseOptions = configuration.baseOptions;
            }

            const localVarRequestOptions = { method: 'POST', ...baseOptions, ...options};
            const localVarHeaderParameter = {} as any;
            const localVarQueryParameter = {} as any;

            // authentication basicAuth required
            // http basic authentication required
            setBasicAuthToObject(localVarRequestOptions, configuration)

            // authentication bearerAuth required
            // http bearer authentication required
            await setBearerAuthToObject(localVarHeaderParameter, configuration)


    
            localVarHeaderParameter['Content-Type'] = 'application/json';

            setSearchParams(localVarUrlObj, localVarQueryParameter);
            let headersFromBaseOptions = baseOptions && baseOptions.headers ? baseOptions.headers : {};
            localVarRequestOptions.headers = {...localVarHeaderParameter, ...headersFromBaseOptions, ...options.headers};
            localVarRequestOptions.data = serializeDataIfNeeded(s3OsProperties, localVarRequestOptions, configuration)

            return {
                url: toPathString(localVarUrlObj),
                options: localVarRequestOptions,
            };
        },
    }
};

/**
 * PolicyConfigValidationControllerApi - functional programming interface
 * @export
 */
export const PolicyConfigValidationControllerApiFp = function(configuration?: Configuration) {
    const localVarAxiosParamCreator = PolicyConfigValidationControllerApiAxiosParamCreator(configuration)
    return {
        /**
         * 
         * @param {S3OsProperties} s3OsProperties 
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        async validatePolicyConfig(s3OsProperties: S3OsProperties, options?: RawAxiosRequestConfig): Promise<(axios?: AxiosInstance, basePath?: string) => AxiosPromise<void>> {
            const localVarAxiosArgs = await localVarAxiosParamCreator.validatePolicyConfig(s3OsProperties, options);
            const localVarOperationServerIndex = configuration?.serverIndex ?? 0;
            const localVarOperationServerBasePath = operationServerMap['PolicyConfigValidationControllerApi.validatePolicyConfig']?.[localVarOperationServerIndex]?.url;
            return (axios, basePath) => createRequestFunction(localVarAxiosArgs, globalAxios, BASE_PATH, configuration)(axios, localVarOperationServerBasePath || basePath);
        },
    }
};

/**
 * PolicyConfigValidationControllerApi - factory interface
 * @export
 */
export const PolicyConfigValidationControllerApiFactory = function (configuration?: Configuration, basePath?: string, axios?: AxiosInstance) {
    const localVarFp = PolicyConfigValidationControllerApiFp(configuration)
    return {
        /**
         * 
         * @param {PolicyConfigValidationControllerApiValidatePolicyConfigRequest} requestParameters Request parameters.
         * @param {*} [options] Override http request option.
         * @throws {RequiredError}
         */
        validatePolicyConfig(requestParameters: PolicyConfigValidationControllerApiValidatePolicyConfigRequest, options?: RawAxiosRequestConfig): AxiosPromise<void> {
            return localVarFp.validatePolicyConfig(requestParameters.s3OsProperties, options).then((request) => request(axios, basePath));
        },
    };
};

/**
 * Request parameters for validatePolicyConfig operation in PolicyConfigValidationControllerApi.
 * @export
 * @interface PolicyConfigValidationControllerApiValidatePolicyConfigRequest
 */
export interface PolicyConfigValidationControllerApiValidatePolicyConfigRequest {
    /**
     * 
     * @type {S3OsProperties}
     * @memberof PolicyConfigValidationControllerApiValidatePolicyConfig
     */
    readonly s3OsProperties: S3OsProperties
}

/**
 * PolicyConfigValidationControllerApi - object-oriented interface
 * @export
 * @class PolicyConfigValidationControllerApi
 * @extends {BaseAPI}
 */
export class PolicyConfigValidationControllerApi extends BaseAPI {
    /**
     * 
     * @param {PolicyConfigValidationControllerApiValidatePolicyConfigRequest} requestParameters Request parameters.
     * @param {*} [options] Override http request option.
     * @throws {RequiredError}
     * @memberof PolicyConfigValidationControllerApi
     */
    public validatePolicyConfig(requestParameters: PolicyConfigValidationControllerApiValidatePolicyConfigRequest, options?: RawAxiosRequestConfig) {
        return PolicyConfigValidationControllerApiFp(this.configuration).validatePolicyConfig(requestParameters.s3OsProperties, options).then((request) => request(this.axios, this.basePath));
    }
}

