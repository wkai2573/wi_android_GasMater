/*
java {
  com.公司|me.wkai {
    專案小寫 {
      data {                    //資料相關
        api {                   //api
          ApiConst(object)      //放api常數
          FooApi(interface)     //放api請求fun, Retrofit用
        }
        db {                    //放database & Dao, Room用
          專案Database           //@Database
          FooDao                //@Dao
        }
        model {                 //放物件, Room & Moshi可用
          adapter {             //放轉換器, Moshi用
            FooAdapter(class)   //@FromJson @ToJson
          }
          Foo(class)            //@Entity @JsonClass
          FooItem(class)
        }
        repository {            //放存儲庫, Room & Retrofit用
          FooRepository
        }
      }
      di {                      //依賴注入, Hilt用
        FooApiModule(object)    //@Module
      }
      ui {                    
        compose {}              //放通用compose元件
        screen {                //放各頁面
          foo {
            compose {}          //放此頁專用compose元件
            FooScreen(fun)
            FooViweModel(class) //@HiltViewModel
          }
          bar {
            BarScreen(fun)
            BarViewModel(class)
          }
          Screen(sealed class)  //放路由(聲明各頁)
          MainActivity(class)   //@AndroidEntryPoint
        }
        theme {                 //主題_自動生成
          Color(fun|val)
          Shape(fun|val)
          Theme(fun|val)
          Type(fun|val)
        }
      }
      專案Application(class)     //@HiltAndroidApp, Hilt需要
    }
  }
}

res {
  drawable {
  }
  mipmap {
  }
  values {
    strings.xml
    colors.xml                  //compose的話用不到
    themes.xml                  //compose的話用不到
  }
  xml {
    backup_rules.xml
    data_extraction_rules.xml
    network_security_config.xml //放允許非明文連接(非https)的網域(domain)
  }
}
*/

/* 通常要引用的lib

//==依賴注入== Hilt
implementation 'com.google.dagger:hilt-android:2.42'
kapt 'com.google.dagger:hilt-compiler:2.42'
kapt "androidx.hilt:hilt-compiler:1.0.0"
implementation "androidx.hilt:hilt-navigation-compose:1.0.0"    //hilt-導航

//==請求== Retrofit2
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-moshi:2.9.0'   //轉換器-Moshi
//implementation 'com.squareup.retrofit2:converter-scalars:2.9.0' //轉換器-標準type

//==Json解析== Moshi, https://github.com/square/moshi
implementation "com.squareup.moshi:moshi-kotlin:1.13.0"
kapt "com.squareup.moshi:moshi-kotlin-codegen:1.13.0"

//==資料庫== Room
// https://developer.android.com/jetpack/androidx/releases/room#kts
implementation "androidx.room:room-runtime:2.4.2"
kapt "androidx.room:room-compiler:2.4.2"
implementation "androidx.room:room-ktx:2.4.2"

//==Compose相關==
implementation "androidx.compose.material:material-icons-extended:1.1.1" //icon
implementation "androidx.navigation:navigation-compose:2.5.0"            //導航

//Retromock: retrofit假資料測試
implementation "co.infinum:retromock:1.1.0"

//Coil: 非同步圖片
implementation "io.coil-kt:coil-compose:2.1.0"

*/

