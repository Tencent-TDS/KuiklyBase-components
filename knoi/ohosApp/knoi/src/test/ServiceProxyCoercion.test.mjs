import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

/**
 * Load the TypeScript helper without a HarmonyOS toolchain.
 * Node 22 can strip types; the helper has no native imports.
 */
const here = dirname(fileURLToPath(import.meta.url));
const helperUrl = pathToFileURL(
  join(here, '../main/ets/ServiceProxyCoercion.ts')
).href;

const { isJsServiceProxyCoercionProp, getJsServiceProxyCoercionHandler } =
  await import(helperUrl);

function createServiceProxy(service, callService) {
  const target = {};
  return new Proxy(target, {
    get: (target, prop) => {
      const coercion = getJsServiceProxyCoercionHandler(service, prop);
      if (coercion !== undefined) {
        return coercion;
      }
      return function (...args) {
        return callService(service, target, String(prop), ...args);
      };
    }
  });
}

describe('service proxy JS coercion', () => {
  it('treats Symbol.toPrimitive, valueOf, and toString as coercion props', () => {
    assert.equal(isJsServiceProxyCoercionProp(Symbol.toPrimitive), true);
    assert.equal(isJsServiceProxyCoercionProp('valueOf'), true);
    assert.equal(isJsServiceProxyCoercionProp('toString'), true);
    assert.equal(isJsServiceProxyCoercionProp('createController'), false);
  });

  it('does not forward ToPrimitive / valueOf / toString as service methods', () => {
    const calls = [];
    const proxy = createServiceProxy('WorkPlayerControllerService', (...args) => {
      calls.push(args);
      return 'controller';
    });

    // crashCase: store the proxy in a local, then coerce / call a method
    const stored = proxy;
    const primitive = stored[Symbol.toPrimitive]('default');
    const asString = `${stored}`;
    const valueOf = stored.valueOf();
    const toString = stored.toString();
    const controller = stored.createController();

    assert.equal(primitive, 'WorkPlayerControllerService');
    assert.equal(asString, 'WorkPlayerControllerService');
    assert.equal(valueOf, 'WorkPlayerControllerService');
    assert.equal(toString, 'WorkPlayerControllerService');
    assert.equal(controller, 'controller');
    assert.equal(calls.length, 1);
    assert.equal(calls[0][0], 'WorkPlayerControllerService');
    assert.equal(calls[0][2], 'createController');
  });

  it('still forwards real methods when chained without storing', () => {
    const calls = [];
    const proxy = createServiceProxy('WorkPlayerControllerService', (...args) => {
      calls.push(args);
      return 'controller';
    });

    const controller = proxy.createController();
    assert.equal(controller, 'controller');
    assert.equal(calls.length, 1);
    assert.equal(calls[0][2], 'createController');
  });
});
