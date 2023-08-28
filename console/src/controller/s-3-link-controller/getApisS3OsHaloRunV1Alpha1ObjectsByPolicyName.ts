import request from "@/utils/request";
import { S3ListResult, DeepRequired } from "../../interface";

/**
 * /apis/s3os.halo.run/v1alpha1/objects/{policyName}
 */
export function getApisS3OsHaloRunV1Alpha1ObjectsByPolicyName(params: GetApisS3OsHaloRunV1Alpha1ObjectsByPolicyNameParams) {
    const paramsInput = {
        continuationToken: params.continuationToken,
        continuationObject: params.continuationObject,
        pageSize: params.pageSize,
        unlinked: params.unlinked,
    };
    return request.get<DeepRequired<S3ListResult>>(`/apis/s3os.halo.run/v1alpha1/objects/${params.policyName}`, {
        params: paramsInput,
    });
}

interface GetApisS3OsHaloRunV1Alpha1ObjectsByPolicyNameParams {
    policyName: any;
    continuationToken?: any;
    continuationObject?: any;
    pageSize: any;
    unlinked?: any;
}
