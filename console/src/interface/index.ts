export * from "./apiTypes/LinkRequest";
export * from "./apiTypes/LinkResult";
export * from "./apiTypes/LinkResultItem";
export * from "./apiTypes/Metadata";
export * from "./apiTypes/ObjectVo";
export * from "./apiTypes/Policy";
export * from "./apiTypes/PolicySpec";
export * from "./apiTypes/S3ListResult";

export type Primitive = undefined | null | boolean | string | number | symbol;
export type DeepRequired<T> = T extends Primitive ? T : keyof T extends never ? T : { [K in keyof T]-?: DeepRequired<T[K]> };
