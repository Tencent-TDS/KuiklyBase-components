/**
 * JS/ArkTS coerces an object via these well-known properties (ToPrimitive).
 * The service Proxy must treat them as no-ops, not as service methods.
 * See https://github.com/Tencent-TDS/KuiklyBase-components/issues/35
 */
export function isJsServiceProxyCoercionProp(prop: string | symbol): boolean {
  return prop === Symbol.toPrimitive || prop === 'valueOf' || prop === 'toString';
}

/**
 * Return a primitive-coercion handler for the service Proxy, or undefined
 * when [prop] is a real service method name.
 */
export function getJsServiceProxyCoercionHandler(
  serviceName: string,
  prop: string | symbol
): ((hint?: string) => string) | undefined {
  if (!isJsServiceProxyCoercionProp(prop)) {
    return undefined;
  }
  return (_hint?: string) => serviceName;
}
