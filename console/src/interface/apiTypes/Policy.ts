import { Metadata, PolicySpec } from "../../interface";

export interface Policy {
    apiVersion: string;
    kind: string;
    metadata: Metadata;
    spec: PolicySpec;
}
