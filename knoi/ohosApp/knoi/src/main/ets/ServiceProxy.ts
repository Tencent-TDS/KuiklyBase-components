import { callService } from './knoi';
import { getJsServiceProxyCoercionHandler } from './ServiceProxyCoercion';

export function getService<R>(service: string): R {

  const target = {}
  return new Proxy(target, {
    get: (target, prop, receiver) => {
      const coercion = getJsServiceProxyCoercionHandler(service, prop);
      if (coercion !== undefined) {
        return coercion;
      }
      return function (...args) {
        return callService(service, target, String(prop), ...args);
      };
    }
  }) as R;
}
