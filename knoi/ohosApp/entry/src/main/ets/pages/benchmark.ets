import systemDateTime from '@ohos.systemDateTime';
import { bind, getService, invoke, registerServiceProvider, registerSingleServiceProvider, setup, unBind } from "@kuiklybase/knoi"
import { ArrayList } from '@kit.ArkTS';
import { BenchmarkForwordImpl, BenchmarkImpl } from '../knoi/benchmarkImple';

@Entry
@Component
struct Benchmark {
  @State message: string = 'Hello World';

  onPageShow(): void {
    registerServiceProvider("BenchMarkConsumer",true,BenchmarkImpl)
    registerServiceProvider("BenchMarkConsumerForWord",true,BenchmarkForwordImpl)
  }

  build() {
    RelativeContainer() {
      Text(this.message)
        .id('BenchmarkHelloWorld')
        .fontSize(50)
        .fontWeight(FontWeight.Bold)
        .alignRules({
          center: { anchor: '__container__', align: VerticalAlign.Center },
          middle: { anchor: '__container__', align: HorizontalAlign.Center }
        })
        .onClick(()=>{
          invoke("oneKeyBenchMark")
        })
    }
    .height('100%')
    .width('100%')
  }
}