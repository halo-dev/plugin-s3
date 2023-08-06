import { ObjectVo } from "../../interface";

export interface S3ListResult {
    currentContinuationObject?: string;
    currentToken?: string;
    hasMore?: boolean;
    nextContinuationObject?: string;
    nextToken?: string;
    objects?: ObjectVo[];
}
