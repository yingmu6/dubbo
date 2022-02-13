/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.configurator.override;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.cluster.configurator.AbstractConfigurator;

/**
 * OverrideConfigurator
 *
 */
public class OverrideConfigurator extends AbstractConfigurator {

    public OverrideConfigurator(URL url) {
        super(url);
    }

    @Override
    public URL doConfigure(URL currentUrl, URL configUrl) { //实现url参数覆盖，本质是url参数Map的覆盖
        return currentUrl.addParameters(configUrl.getParameters());
    }

    /**
     * super和this的异同
     * 1）super（参数）：调用基类中的某一个构造函数（应该为构造函数中的第一条语句）
     * 2）this（参数）：调用本类中另一种形成的构造函数（应该为构造函数中的第一条语句）
     * 3）super:　它引用当前对象的直接父类中的成员（用来访问直接父类中被隐藏的父类中成员数据或函数，基类与派生类中有相同成员定义时如：super.变量名 super.成员函数据名（实参）
     * 4）this：它代表当前对象名（在程序中易产生二义性之处，应使用this来指明当前对象；如果函数的形参与类中的成员数据同名，这时需用this来指明成员变量名）
     * 5）调用super()必须写在子类构造方法的第一行，否则编译不通过。每个子类构造方法的第一条语句，都是隐含地调用super()，
     * 如果父类没有这种形式的构造函数，那么在编译的时候就会报错。（若父类没有无参的构造方法，子类就需要显示调用super(参数)方法 ）
     *
     * https://www.cnblogs.com/hasse/p/5023392.html
     */

    /**
     * 子类与父类的构造函数
     * 1）构造函数是不能继承的，只是用来在子类调用,（如果父类没有无参构造函数，创建子类时，必须在子类构造函数代码体的第一行显式调用super(参数) 父类的有参数构造函数，否则不能编译）;
     * 2）如果父类有无参构造函数,那么在创建子类时可以不显式调用父类构造函数,系统会默认调用父类的无参构造函数super();
     * 3）如果父类没有无参构造函数,那系统就调不了默认的无参构造函数了,所以不显示调用编译也就无法通过了
     *
     * 构造函数是不能继承的，要么显示调用super()，要么隐式调用super()
     *
     * https://www.jianshu.com/p/f7934687e420
     */

}
