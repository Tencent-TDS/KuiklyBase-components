/*
 * Copyright (C) 2023 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
export declare function init(): void;

export declare function setup(soName: string, debug: boolean): void;

export declare function destroy(): void;

export declare function create_function_waiter(): number;

export declare function notify_function_waiter(id: number, result: string, length: number): void;

export declare function wait_on_function_waiter(id: number): string;
