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


// May contain unused imports in some cases
// @ts-ignore
import type { ObjectVo } from './object-vo';

/**
 * 
 * @export
 * @interface S3ListResult
 */
export interface S3ListResult {
    /**
     * 
     * @type {string}
     * @memberof S3ListResult
     */
    'currentContinuationObject'?: string;
    /**
     * 
     * @type {string}
     * @memberof S3ListResult
     */
    'currentToken'?: string;
    /**
     * 
     * @type {boolean}
     * @memberof S3ListResult
     */
    'hasMore'?: boolean;
    /**
     * 
     * @type {string}
     * @memberof S3ListResult
     */
    'nextContinuationObject'?: string;
    /**
     * 
     * @type {string}
     * @memberof S3ListResult
     */
    'nextToken'?: string;
    /**
     * 
     * @type {Array<ObjectVo>}
     * @memberof S3ListResult
     */
    'objects'?: Array<ObjectVo>;
}
