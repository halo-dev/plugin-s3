export interface Metadata {
    annotations?: MetadataAnnotations;
    creationTimestamp?: string;
    deletionTimestamp?: string;
    finalizers?: string[];
    /** The name field will be generated automatically according to the given generateName field */
    generateName?: string;
    labels?: MetadataLabels;
    /** Metadata name */
    name: string;
    version?: number;
}

export interface MetadataAnnotations {
    [key: string]: any;
}

export interface MetadataLabels {
    [key: string]: any;
}
