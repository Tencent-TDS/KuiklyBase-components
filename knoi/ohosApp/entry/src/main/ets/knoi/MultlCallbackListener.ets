import { MultiCallbackListener } from './callback';

export class MultiCallbackListenerImpl implements MultiCallbackListener {
  onStart() {
    console.log("knoi-sample MultiCallbackListener#onStart");
  }

  onEnd() {
    console.log("knoi-sample MultiCallbackListener#onEnd");
  }

  onSuccess() {
    console.log("knoi-sample MultiCallbackListener#onSuccess");
  }

  onFail() {
    console.log("knoi-sample MultiCallbackListener#onFail");
  }
}