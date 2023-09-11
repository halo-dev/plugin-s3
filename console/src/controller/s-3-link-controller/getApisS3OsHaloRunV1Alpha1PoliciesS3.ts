import request from "@/utils/request";
import { DeepRequired } from "../../interface";

/**
 * /apis/s3os.halo.run/v1alpha1/policies/s3
 */
export function getApisS3OsHaloRunV1Alpha1PoliciesS3() {
    return request.get<DeepRequired<any>>(`/apis/s3os.halo.run/v1alpha1/policies/s3`);
}
