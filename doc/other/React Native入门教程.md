## 一、React Native 概述

### 1.1 什么是 React Native

React Native（简称 RN）是 Meta（Facebook）开源的跨平台移动开发框架，核心理念是 **"Learn once, write everywhere"**。它使用 JavaScript/TypeScript + React 来构建原生移动应用。

### 1.2 三大跨平台方案对比

| 维度 | Jetpack Compose | Flutter | React Native |
|------|----------------|---------|--------------|
| **语言** | Kotlin | Dart | JavaScript / TypeScript |
| **UI 描述方式** | Kotlin DSL（声明式） | Dart Widget 树（声明式） | JSX（声明式） |
| **渲染引擎** | 原生 Android View 系统 | 自绘引擎（Skia / Impeller） | 原生组件（Fabric 新架构）/ JS 驱动 |
| **状态管理** | State / StateHolder / ViewModel | setState / Provider / Bloc / Riverpod | useState / useReducer / Redux / Zustand |
| **热重载** | 支持（Apply Changes） | 支持（Hot Reload / Hot Restart） | 支持（Fast Refresh） |
| **跨平台** | 仅 Android | Android / iOS / Web / 桌面 | Android / iOS / Web / 桌面 |
| **性能** | ★★★★★（原生） | ★★★★☆（接近原生） | ★★★★☆（新架构大幅提升） |
| **学习曲线** | 需 Kotlin 基础 | 需 Dart 基础 | 需 JS/TS 基础 |
| **社区生态** | Android 官方 | Google 官方 | Meta 主导，社区庞大 |

### 1.3 React Native 架构演进

```
旧架构（Bridge）：
  JS 线程  ←→  JSON Bridge（序列化/反序列化）  ←→  原生 UI 线程
  问题：Bridge 是性能瓶颈，大量数据传递时卡顿

新架构（Fabric + TurboModules + JSI）：
  JS 线程  ←→  JSI（C++ 接口，直接调用）  ←→  原生线程
  优势：同步调用、无序列化开销、支持同步布局
```

> **对比 Flutter**：Flutter 从一开始就使用自绘引擎（Skia），没有 Bridge 的概念，所有 Widget 直接绘制到 Canvas 上。RN 新架构的 JSI 类似于消除了"中间商"，让 JS 直接操作原生对象。

---

## 二、环境搭建与项目结构

### 2.1 环境准备

```bash
# 安装 Node.js（推荐 LTS 版本）
# 使用 nvm 管理版本
nvm install --lts

# 使用 Expo（推荐新手）或 React Native CLI
npx create-expo-app MyRnApp       # Expo 方式（推荐）
npx react-native@latest init MyRnApp  # CLI 方式

# 启动开发服务器
cd MyRnApp
npx expo start                     # Expo
npx react-native start             # CLI
```

### 2.2 项目结构

```
MyRnApp/
├── App.js (或 App.tsx)            ← 入口文件（类似 MainActivity.kt）
├── index.js                       ← JS 入口注册（注册 AppRegistry）
├── package.json                   ← 依赖管理（类似 build.gradle）
├── babel.config.js                ← Babel 编译配置
├── app.json                       ← App 元数据配置
├── ios/                           ← iOS 原生工程
├── android/                       ← Android 原生工程
├── src/
│   ├── components/                ← 可复用组件（类似 Composable 函数）
│   ├── screens/                   ← 页面
│   ├── navigation/                ← 路由导航
│   ├── hooks/                     ← 自定义 Hook
│   ├── store/                     ← 状态管理
│   ├── services/                  ← 网络请求等
│   └── utils/                     ← 工具函数
└── node_modules/                  ← 依赖包（类似 .gradle 缓存）
```

> **对比**：
> - `package.json` ≈ `build.gradle`，管理项目依赖
> - `node_modules/` ≈ Gradle 下载的依赖库
> - `npm` / `yarn` ≈ Gradle 依赖解析
> - `App.js` ≈ `MainActivity.kt` + `setContent {}`

---

## 三、JSX 与组件基础

### 3.1 JSX 是什么

JSX 是 JavaScript 的语法扩展，允许在 JS 中直接写"类 HTML"标记。它是 React 组件的核心表达方式。

```jsx
// JSX 写法
function Greeting({ name }) {
  return <Text style={styles.title}>Hello, {name}!</Text>;
}

// 编译后等价于（类似 Compose 的函数调用）
function Greeting(props) {
  return React.createElement(Text, { style: styles.title }, `Hello, ${props.name}!`);
}
```

> **对比 Compose**：Compose 用 Kotlin 函数调用构建 UI，RN 用 JSX 构建 UI。两者都是声明式，但 Compose 是 Kotlin 编译器插件处理，JSX 是 Babel 编译为 `createElement` 调用。

> **对比 Flutter**：Flutter 用 Dart 构造函数构造 Widget 对象，RN 的 JSX 编译后也是函数调用，但返回的是"虚拟 DOM 描述"（React Element），而非直接创建原生对象。

### 3.2 组件（Component）

React Native 的组件分为**函数组件**和**类组件**，现代开发推荐使用函数组件 + Hooks。

```jsx
// ===== 函数组件（推荐，类似 Compose 的 @Composable 函数）=====
function Counter() {
  const [count, setCount] = useState(0);  // 类似 Compose 的 remember { mutableStateOf(0) }
  
  return (
    <View>
      <Text>Count: {count}</Text>
      <Button title="增加" onPress={() => setCount(count + 1)} />
    </View>
  );
}

// ===== 类组件（传统写法，类似 Flutter 的 StatefulWidget）=====
class Counter extends React.Component {
  constructor(props) {
    super(props);
    this.state = { count: 0 };  // 类似 Flutter 的 State 类
  }

  render() {  // 类似 Flutter 的 build() 方法
    return (
      <View>
        <Text>Count: {this.state.count}</Text>
        <Button title="增加" onPress={() => this.setState({ count: this.state.count + 1 })} />
      </View>
    );
  }
}
```

> **三方对比**：
>
> | 概念 | Compose | Flutter | React Native |
> |------|---------|---------|--------------|
> | 无状态组件 | `@Composable fun` | `StatelessWidget` | 函数组件（无 Hook） |
> | 有状态组件 | `mutableStateOf` + `remember` | `StatefulWidget` + `State` | `useState` Hook / 类组件 |
> | 状态声明 | `val state = remember { mutableStateOf(0) }` | `State<MyState>` 类 | `const [state, setState] = useState(0)` |
> | UI 构建 | Kotlin DSL | `build()` 返回 Widget 树 | `return` JSX |
> | 重组/重建触发 | State 值变化自动 recompose | `setState()` 触发 rebuild | `setState()` 触发 re-render |

### 3.3 Props（属性传递）

Props 是组件的输入参数，**只读、不可变**，由父组件传给子组件。

```jsx
// 定义组件接收 Props（类似 Compose 函数的参数）
function UserCard({ name, age, avatar }) {
  return (
    <View style={styles.card}>
      <Image source={{ uri: avatar }} style={styles.avatar} />
      <Text>{name}</Text>
      <Text>Age: {age}</Text>
    </View>
  );
}

// 使用（类似 Compose 中调用 @Composable 函数传参）
<UserCard name="张三" age={25} avatar="https://example.com/avatar.jpg" />

// Props 类型定义（TypeScript，类似 Kotlin 数据类的参数声明）
interface UserCardProps {
  name: string;
  age: number;
  avatar?: string;  // 可选属性
}
```

> **对比**：
> - RN Props ≈ Compose 函数参数 ≈ Flutter Widget 构造函数参数
> - RN Props 是**只读**的，子组件不能修改（和 Compose / Flutter 一样）
> - TypeScript 的 `interface` 提供编译时类型检查，类似 Kotlin 的类型系统

### 3.4 基础组件对照表

| 用途 | Compose | Flutter | React Native |
|------|---------|---------|--------------|
| 容器/布局 | `Column` / `Row` / `Box` | `Column` / `Row` / `Stack` | `View`（默认纵向排列） |
| 文本 | `Text` | `Text` | `Text` |
| 图片 | `Image` / `AsyncImage` | `Image` / `Image.network` | `Image` |
| 按钮 | `Button` | `ElevatedButton` 等 | `TouchableOpacity` / `Pressable` |
| 输入框 | `TextField` | `TextField` | `TextInput` |
| 列表 | `LazyColumn` | `ListView` | `FlatList` |
| 网格 | `LazyVerticalGrid` | `GridView` | `FlatList` + `numColumns` |
| 滚动 | `Modifier.verticalScroll` | `SingleChildScrollView` | `ScrollView` |
| 分割线 | `Divider` | `Divider` | `View` + 样式模拟 |
| 图标 | `Icon` | `Icon` | 需第三方库（如 `react-native-vector-icons`） |

---

## 四、样式系统

### 4.1 StyleSheet

React Native 使用 JavaScript 对象描述样式，通过 `StyleSheet.create()` 创建（类似 CSS-in-JS）。

```jsx
import { StyleSheet } from 'react-native';

const styles = StyleSheet.create({
  container: {
    flex: 1,              // 类似 Flexbox 的 flex-grow
    backgroundColor: '#fff',
    alignItems: 'center',  // 类似 Column 的 horizontalAlignment
    justifyContent: 'center', // 类似 Column 的 verticalArrangement
  },
  text: {
    fontSize: 18,
    color: '#333',
    fontWeight: 'bold',
    marginTop: 16,
  },
});

// 使用
<View style={styles.container}>
  <Text style={styles.text}>Hello</Text>
</View>
```

### 4.2 Flexbox 布局（核心）

RN 的布局系统基于 **Flexbox**，默认方向是 `column`（和 Flutter 的 `Column` 一致）。

```jsx
// 纵向排列（默认）—— 类似 Compose 的 Column / Flutter 的 Column
<View style={{ flexDirection: 'column' }}>
  <View style={{ flex: 1, backgroundColor: 'red' }} />
  <View style={{ flex: 2, backgroundColor: 'blue' }} />
</View>

// 横向排列 —— 类似 Compose 的 Row / Flutter 的 Row
<View style={{ flexDirection: 'row' }}>
  <View style={{ flex: 1 }} />
  <View style={{ flex: 1 }} />
</View>

// 绝对定位 —— 类似 Compose 的 Box + Alignment / Flutter 的 Stack + Positioned
<View style={{ position: 'absolute', top: 10, right: 10 }}>
  <Text>Floating</Text>
</View>
```

> **对比**：
> - RN 的 `flex: 1` ≈ Compose 的 `Modifier.weight(1f)` ≈ Flutter 的 `Expanded(flex: 1)`
> - RN 默认 `flexDirection: 'column'`，而 Web CSS 默认是 `row`，这是 RN 的特殊设定
> - RN 不支持 `flexDirection: 'row'` + `flexWrap: 'wrap'` 的 FlowLayout（需第三方库）

### 4.3 样式与 Compose Modifier / Flutter Widget 样式的对比

```jsx
// ===== React Native =====
<Text style={{ fontSize: 16, color: 'red', margin: 8, padding: 4 }}>
  Hello
</Text>

// ===== Compose（Kotlin）=====
Text(
  text = "Hello",
  fontSize = 16.sp,
  color = Color.Red,
  modifier = Modifier.padding(8.dp).then(Modifier.padding(4.dp))
)

// ===== Flutter（Dart）=====
Text(
  'Hello',
  style: TextStyle(fontSize: 16, color: Colors.red),
)
// 外层 padding 用 Padding widget 或 Container
```

> **关键区别**：
> - RN 的样式是**扁平的 JS 对象**，没有类型安全（除非用 TypeScript）
> - Compose 的 Modifier 是**链式调用**，编译时类型安全
> - Flutter 的样式通过 **Widget 嵌套** 实现（Padding、Container 等）
> - RN 的数值单位是**逻辑像素（dp）**，无需手动转换

---

## 五、State（状态管理）

### 5.1 useState —— 最基础的状态

`useState` 是 React 最常用的 Hook，用于在函数组件中声明状态变量。

```jsx
function Counter() {
  // [当前值, 更新函数] = useState(初始值)
  const [count, setCount] = useState(0);

  return (
    <View>
      <Text>Count: {count}</Text>
      <Button title="+1" onPress={() => setCount(count + 1)} />
      <Button title="重置" onPress={() => setCount(0)} />
    </View>
  );
}
```

> **对比**：
>
> ```kotlin
> // Compose 写法
> @Composable
> fun Counter() {
>     var count by remember { mutableStateOf(0) }
>     Text("Count: $count")
>     Button(onClick = { count++ }) { Text("+1") }
> }
> ```
>
> ```dart
> // Flutter 写法
> class Counter extends StatefulWidget {
>   @override
>   State<Counter> createState() => _CounterState();
> }
> class _CounterState extends State<Counter> {
>   int count = 0;
>   @override
>   Widget build(BuildContext context) {
>     return Column(
>       children: [
>         Text('Count: $count'),
>         ElevatedButton(onPressed: () => setState(() => count++), child: Text('+1')),
>       ],
>     );
>   }
> }
> ```

### 5.2 useEffect —— 副作用处理

`useEffect` 用于处理副作用（网络请求、订阅、定时器等），类似 Compose 的 `LaunchedEffect` 和 Flutter 的 `initState` + `didChangeDependencies`。

```jsx
function UserProfile({ userId }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // 类似 Compose 的 LaunchedEffect(userId) { ... }
  useEffect(() => {
    setLoading(true);
    fetchUser(userId).then(data => {
      setUser(data);
      setLoading(false);
    });

    // 返回的函数 = 清理逻辑（类似 DisposableEffect 的 onDispose）
    return () => {
      console.log('组件卸载或 userId 变化时清理');
    };
  }, [userId]);  // 依赖数组：userId 变化时重新执行

  if (loading) return <Text>Loading...</Text>;
  return <Text>{user.name}</Text>;
}
```

> **依赖数组对照**：
>
> | useEffect 写法 | Compose 等价 | 说明 |
> |---------------|-------------|------|
> | `useEffect(() => {}, [])` | `LaunchedEffect(Unit) {}` | 仅执行一次（挂载时） |
> | `useEffect(() => {}, [dep])` | `LaunchedEffect(dep) {}` | dep 变化时重新执行 |
> | `useEffect(() => { return cleanup }, [dep])` | `DisposableEffect(dep) { onDispose { cleanup } }` | 带清理逻辑 |
> | 无 useEffect | `SideEffect {}` | 每次渲染后执行 |

### 5.3 useRef —— 持有可变引用

`useRef` 创建一个在组件生命周期内持久的可变引用，修改它**不会触发重新渲染**。

```jsx
function Timer() {
  const [seconds, setSeconds] = useState(0);
  const intervalRef = useRef(null);  // 类似 Compose 的 remember { mutableStateOf<Timer?>(null) }

  useEffect(() => {
    intervalRef.current = setInterval(() => setSeconds(s => s + 1), 1000);
    return () => clearInterval(intervalRef.current);
  }, []);

  const stop = () => clearInterval(intervalRef.current);

  return (
    <View>
      <Text>Seconds: {seconds}</Text>
      <Button title="停止" onPress={stop} />
    </View>
  );
}
```

> **对比**：
> - `useRef` ≈ Compose 的 `remember { mutableStateOf(xxx) }`（不触发重组的引用）
> - `useRef` ≈ Flutter 中 State 类的普通字段
> - 常见用途：保存定时器 ID、动画控制器、DOM/原生组件引用

### 5.4 useMemo / useCallback —— 性能优化

```jsx
function ExpensiveList({ items, filter }) {
  // useMemo：缓存计算结果，类似 Compose 的 remember(filter) { computeExpensive(items) }
  const filtered = useMemo(
    () => items.filter(item => item.name.includes(filter)),
    [items, filter]  // 依赖变化时重新计算
  );

  // useCallback：缓存函数引用，避免子组件不必要的重新渲染
  const handlePress = useCallback((id) => {
    console.log('Pressed:', id);
  }, []);  // 空依赖 = 函数引用永不变

  return <FlatList data={filtered} renderItem={({ item }) => (
    <ListItem item={item} onPress={handlePress} />
  )} />;
}
```

> **对比 Compose**：
> - `useMemo(deps)` ≈ `remember(*deps) { computation }`
> - `useCallback(deps)` ≈ `remember(*deps) { lambda }`
> - 目的相同：避免每次渲染/重组时重复创建对象

---

## 六、列表与滚动

### 6.1 FlatList（核心列表组件）

```jsx
import { FlatList } from 'react-native';

function UserList({ users }) {
  // renderItem 类似 Compose 的 LazyColumn 的 item {} 或 Flutter 的 ListView.builder
  const renderItem = ({ item, index }) => (
    <View style={styles.item}>
      <Text>{index + 1}. {item.name}</Text>
      <Text>{item.email}</Text>
    </View>
  );

  return (
    <FlatList
      data={users}                    // 数据源（类似 LazyColumn 的 items）
      renderItem={renderItem}         // 渲染每一项
      keyExtractor={item => item.id}  // 唯一 key（类似 Flutter 的 key）
      ItemSeparatorComponent={() => <View style={styles.divider} />}
      ListHeaderComponent={() => <Text style={styles.header}>用户列表</Text>}
      ListEmptyComponent={() => <Text>暂无数据</Text>}
      onEndReached={() => loadMore()}       // 滚动到底部加载更多
      onEndReachedThreshold={0.5}           // 距底部 50% 时触发
      refreshing={isRefreshing}             // 下拉刷新状态
      onRefresh={() => refreshData()}       // 下拉刷新回调
    />
  );
}
```

> **三方对比**：
>
> | 特性 | Compose LazyColumn | Flutter ListView | RN FlatList |
> |------|-------------------|-----------------|-------------|
> | 懒加载 | ✅ 内置 | ❌ 需 ListView.builder | ✅ 内置 |
> | 下拉刷新 | `pullRefresh` Modifier | `RefreshIndicator` | `refresh` + `onRefresh` |
> | 加载更多 | 手动监听滚动 | 监听 ScrollController | `onEndReached` 内置 |
> | 分组/头尾 | `stickyHeader` / item 类型 | `SliverList` 系列 | `ListHeaderComponent` 等 |
> | 分隔线 | `item {}` 手动 | `Divider` in itemBuilder | `ItemSeparatorComponent` |

### 6.2 SectionList（分组列表）

```jsx
<SectionList
  sections={[
    { title: 'A', data: ['Alice', 'Amy'] },
    { title: 'B', data: ['Bob', 'Bill'] },
  ]}
  renderItem={({ item }) => <Text>{item}</Text>}
  renderSectionHeader={({ section }) => <Text>{section.title}</Text>}
/>
```

### 6.3 ScrollView

```jsx
// 简单滚动 —— 类似 Compose 的 Modifier.verticalScroll() / Flutter 的 SingleChildScrollView
<ScrollView>
  <Text>内容1</Text>
  <Text>内容2</Text>
  <Text>内容3</Text>
</ScrollView>

// 水平滚动
<ScrollView horizontal showsHorizontalScrollIndicator={false}>
  <View style={{ width: 100, height: 100, backgroundColor: 'red' }} />
  <View style={{ width: 100, height: 100, backgroundColor: 'blue' }} />
</ScrollView>
```

---

## 七、事件与交互

### 7.1 触摸事件

```jsx
import { TouchableOpacity, Pressable, Text } from 'react-native';

// TouchableOpacity —— 点击时有透明度变化（最常用）
<TouchableOpacity onPress={() => console.log('pressed!')} activeOpacity={0.7}>
  <Text>点我</Text>
</TouchableOpacity>

// Pressable —— 更灵活，可获取按压状态（新 API，推荐）
<Pressable
  onPress={() => console.log('点击')}
  onLongPress={() => console.log('长按')}
  onPressIn={() => console.log('按下')}
  onPressOut={() => console.log('抬起')}
  style={({ pressed }) => [
    { backgroundColor: pressed ? '#ddd' : '#fff' }  // 根据按压状态切换样式
  ]}
>
  <Text>Pressable 按钮</Text>
</Pressable>
```

> **对比**：
> - `TouchableOpacity` ≈ Compose 的 `clickable {}` + `alpha` 动画 ≈ Flutter 的 `InkWell`
> - `Pressable` ≈ Compose 的 `pointerInput {}` ≈ Flutter 的 `GestureDetector`
> - RN 没有内置水波纹效果，需自定义或使用 `TouchableHighlight`

### 7.2 输入处理

```jsx
function SearchBar() {
  const [text, setText] = useState('');

  return (
    <TextInput
      style={styles.input}
      placeholder="搜索..."
      value={text}
      onChangeText={setText}           // 文本变化回调
      onFocus={() => console.log('聚焦')}
      onBlur={() => console.log('失焦')}
      onSubmitEditing={() => search(text)}  // 提交（键盘回车）
      keyboardType="default"
      returnKeyType="search"
      autoFocus={false}
    />
  );
}
```

> **对比**：
> - `TextInput` ≈ Compose 的 `TextField` ≈ Flutter 的 `TextField`
> - `onChangeText` ≈ Compose 的 `onValueChange` ≈ Flutter 的 `onChanged`
> - RN 的 `TextInput` 功能相对简单，高级功能需要第三方库

---

## 八、导航（Navigation）

### 8.1 React Navigation

React Native 没有内置导航，官方推荐 **React Navigation** 库。

```bash
npm install @react-navigation/native @react-navigation/native-stack
npx expo install react-native-screens react-native-safe-area-context
```

```jsx
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

const Stack = createNativeStackNavigator();

function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: '首页' }} />
        <Stack.Screen name="Detail" component={DetailScreen} options={{ title: '详情' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
```

### 8.2 页面跳转与参数传递

```jsx
function HomeScreen({ navigation }) {
  return (
    <Button
      title="去详情"
      onPress={() => navigation.navigate('Detail', { id: 123, name: '张三' })}
      // 类似 Compose 的 navController.navigate("detail/123")
      // 类似 Flutter 的 Navigator.push(context, MaterialPageRoute(builder: (_) => DetailScreen(id: 123)))
    />
  );
}

function DetailScreen({ route, navigation }) {
  const { id, name } = route.params;  // 获取参数

  return (
    <View>
      <Text>用户 ID: {id}, 姓名: {name}</Text>
      <Button title="返回" onPress={() => navigation.goBack()} />
    </View>
  );
}
```

### 8.3 底部 Tab 导航

```jsx
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';

const Tab = createBottomTabNavigator();

function MainTabs() {
  return (
    <Tab.Navigator>
      <Tab.Screen name="首页" component={HomeScreen} />
      <Tab.Screen name="发现" component={DiscoverScreen} />
      <Tab.Screen name="我的" component={ProfileScreen} />
    </Tab.Navigator>
  );
}
```

> **三方导航对比**：
>
> | 特性 | Compose Navigation | Flutter Navigator | React Navigation |
> |------|-------------------|-------------------|-----------------|
> | 类型安全 | ✅ 编译时检查 | ❌ 运行时 | ❌ 运行时（TS 部分弥补） |
> | 声明式路由 | ✅ `composable {}` 注册 | ❌ 命令式 push | ✅ 声明式 `<Stack.Screen>` |
> | 深链接 | ✅ 内置支持 | 需第三方 | ✅ 内置支持 |
> | 底部导航 | 自定义实现 | `BottomNavigationBar` | `createBottomTabNavigator` |
> | Drawer | 自定义实现 | `Drawer` Widget | `createDrawerNavigator` |

---

## 九、网络请求

### 9.1 Fetch API

```jsx
function UserList() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    // 类似 Compose 中 LaunchedEffect { viewModel.fetchUsers() }
    fetch('https://api.example.com/users')
      .then(response => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      })
      .then(data => setUsers(data))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <ActivityIndicator size="large" />;
  if (error) return <Text>Error: {error}</Text>;

  return (
    <FlatList
      data={users}
      renderItem={({ item }) => <Text>{item.name}</Text>}
      keyExtractor={item => item.id.toString()}
    />
  );
}
```

### 9.2 Async/Await（推荐写法）

```jsx
async function fetchUsers() {
  try {
    const response = await fetch('https://api.example.com/users');
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 在组件中使用
useEffect(() => {
  fetchUsers().then(setUsers);
}, []);
```

> **对比 Kotlin 协程**：
>
> ```kotlin
> // Kotlin 协程写法
> suspend fun fetchUsers(): List<User> {
>     return withContext(Dispatchers.IO) {
>         val response = httpClient.get("https://api.example.com/users")
>         response.body()
>     }
> }
> ```
>
> - JS 的 `async/await` ≈ Kotlin 的 `suspend` + `withContext`
> - JS 没有协程的 Dispatcher 概念，网络请求天然在后台线程
> - JS 的 `Promise` ≈ Kotlin 的 `Deferred<T>`
> - JS 的 `try/catch` ≈ Kotlin 协程的 `try/catch`

---

## 十、进阶状态管理

### 10.1 Context API（跨组件状态共享）

React 的 Context 类似 Compose 的 `CompositionLocal` 和 Flutter 的 `InheritedWidget` / `Provider`。

```jsx
// 1. 创建 Context（类似 Compose 的 compositionLocalOf {}）
const ThemeContext = React.createContext('light');

// 2. 提供数据（类似 Compose 的 CompositionLocalProvider）
function App() {
  return (
    <ThemeContext.Provider value="dark">
      <Screen />
    </ThemeContext.Provider>
  );
}

// 3. 消费数据
function Screen() {
  const theme = useContext(ThemeContext);  // 类似 Compose 的 LocalTheme.current
  return <Text style={{ color: theme === 'dark' ? '#fff' : '#000' }}>Theme: {theme}</Text>;
}
```

> **三方对比**：
>
> | 特性 | Compose | Flutter | React Native |
> |------|---------|---------|--------------|
> | 向下传递数据 | `CompositionLocal` | `InheritedWidget` / `Provider` | `Context` |
> | 读取方式 | `LocalXxx.current` | `context.watch<Xxx>()` | `useContext(XxxContext)` |
> | 性能问题 | 值变化时所有读取者重组 | `Provider` 精准控制 | 所有消费者重渲染 |

### 10.2 useReducer（复杂状态逻辑）

`useReducer` 类似 Flutter 的 Bloc 模式（单向数据流），适合复杂状态逻辑。

```jsx
// 定义 State 和 Action（类似 Bloc 的 State + Event）
const initialState = { count: 0 };

function reducer(state, action) {
  switch (action.type) {
    case 'increment': return { count: state.count + 1 };
    case 'decrement': return { count: state.count - 1 };
    case 'reset':     return { count: 0 };
    default:          return state;
  }
}

function Counter() {
  const [state, dispatch] = useReducer(reducer, initialState);

  return (
    <View>
      <Text>Count: {state.count}</Text>
      <Button title="+1" onPress={() => dispatch({ type: 'increment' })} />
      <Button title="-1" onPress={() => dispatch({ type: 'decrement' })} />
      <Button title="重置" onPress={() => dispatch({ type: 'reset' })} />
    </View>
  );
}
```

> **对比 Bloc**：
>
> ```dart
> // Flutter Bloc 写法
> // Event
> abstract class CounterEvent {}
> class Increment extends CounterEvent {}
> class Decrement extends CounterEvent {}
>
> // Bloc
> class CounterBloc extends Bloc<CounterEvent, int> {
>   CounterBloc() : super(0) {
>     on<Increment>((event, emit) => emit(state + 1));
>     on<Decrement>((event, emit) => emit(state - 1));
>   }
> }
> ```
>
> - `useReducer` 的 `dispatch` ≈ Bloc 的 `add(event)`
> - `useReducer` 的 `reducer` ≈ Bloc 的 `on<Event>` 处理
> - `useReducer` 更轻量，不需要额外库

### 10.3 第三方状态管理方案

| 方案 | 特点 | 类似 Flutter 方案 | 适用场景 |
|------|------|-------------------|---------|
| **Redux Toolkit** | 单一 Store + Reducer + 中间件 | Bloc（更严格） | 大型项目、需要时间旅行调试 |
| **Zustand** | 轻量、Hook 风格、无 Provider | GetX（简洁） | 中小型项目，推荐首选 |
| **Jotai** | 原子化状态、按需订阅 | Riverpod | 细粒度状态管理 |
| **MobX** | 响应式、Observable | GetX 的 Rx | 喜欢 OOP 风格 |

```jsx
// Zustand 示例（最简洁的状态管理）
import { create } from 'zustand';

// 定义 Store（类似 GetX 的 GetxController）
const useCounterStore = create((set) => ({
  count: 0,
  increment: () => set((state) => ({ count: state.count + 1 })),
  decrement: () => set((state) => ({ count: state.count - 1 })),
}));

// 使用（类似 GetX 的 Obx / GetBuilder）
function Counter() {
  const { count, increment, decrement } = useCounterStore();
  return (
    <View>
      <Text>{count}</Text>
      <Button title="+1" onPress={increment} />
      <Button title="-1" onPress={decrement} />
    </View>
  );
}
```

---

## 十一、自定义 Hook（逻辑复用）

### 11.1 什么是自定义 Hook

自定义 Hook 是以 `use` 开头的函数，用于**复用状态逻辑**（不是复用 UI）。

> **对比**：
> - Compose 中复用逻辑 → 直接写普通函数或 ViewModel
> - Flutter 中复用逻辑 → Mixin / 基类 State / ChangeNotifier
> - RN 中复用逻辑 → **自定义 Hook**（最优雅的方式）

### 11.2 示例

```jsx
// 自定义 Hook：封装网络请求逻辑
function useFetch(url) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    fetch(url)
      .then(res => res.json())
      .then(setData)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [url]);

  return { data, loading, error, refetch: () => {/* ... */} };
}

// 使用（可以在多个组件中复用）
function UserList() {
  const { data: users, loading, error } = useFetch('/api/users');
  if (loading) return <Text>Loading...</Text>;
  if (error) return <Text>Error</Text>;
  return <FlatList data={users} renderItem={...} />;
}

// 自定义 Hook：封装本地存储
function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    const stored = localStorage.getItem(key);
    return stored ? JSON.parse(stored) : initialValue;
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue];
}
```

---

## 十二、动画

### 12.1 Animated API

```jsx
import { Animated, Easing } from 'react-native';

function FadeInView({ children }) {
  const fadeAnim = useRef(new Animated.Value(0)).current;  // 初始值 0

  useEffect(() => {
    Animated.timing(fadeAnim, {
      toValue: 1,
      duration: 1000,
      easing: Easing.ease,
      useNativeDriver: true,  // 在原生线程执行动画，性能更好
    }).start();
  }, []);

  return (
    <Animated.View style={{ opacity: fadeAnim }}>
      {children}
    </Animated.View>
  );
}
```

> **三方对比**：
>
> | 特性 | Compose Animation | Flutter Animation | RN Animated |
> |------|-------------------|-------------------|-------------|
> | 基础 API | `animate*AsState` | `AnimationController` | `Animated.Value` |
> | 声明式动画 | ✅ `tween`/`spring`/`keyframes` | `Tween` + `Curve` | `Animated.timing`/`spring` |
> | 原生线程执行 | ✅ 内置 | ✅ 内置 | 需 `useNativeDriver: true` |
> | 手势驱动动画 | `Transition` | `AnimatedBuilder` | `Animated.event` |
> | 布局动画 | `animateContentSize` | `AnimatedSize` | `LayoutAnimation` |

### 12.2 Reanimated（推荐第三方库）

```jsx
import Animated, { useSharedValue, useAnimatedStyle, withSpring } from 'react-native-reanimated';

function Box() {
  const offset = useSharedValue(0);  // 类似 Compose 的 animate*AsState

  const animatedStyle = useAnimatedStyle(() => {
    return { transform: [{ translateX: offset.value }] };
  });

  return (
    <Animated.View style={[styles.box, animatedStyle]}>
      <Pressable onPress={() => { offset.value = withSpring(200); }} />
    </Animated.View>
  );
}
```

---

## 十三、原生模块与 Bridge

### 13.1 为什么需要原生模块

当 JS 无法直接实现某些功能（如蓝牙、NFC、相机底层 API）时，需要编写原生模块。

### 13.2 创建原生模块（Android 示例）

```kotlin
// Android 原生模块（Kotlin）
class BatteryModule : ReactContextBaseJavaModule(reactContext) {
    override fun getName() = "BatteryModule"

    @ReactMethod
    fun getBatteryLevel(promise: Promise) {
        val bm = reactApplicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        promise.resolve(level)
    }
}

// 注册模块
class BatteryPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext) =
        listOf(BatteryModule(reactContext))
    override fun createViewManagers(reactContext: ReactApplicationContext) = emptyList<ViewManager>()
}
```

```jsx
// JS 端调用
import { NativeModules } from 'react-native';
const { BatteryModule } = NativeModules;

async function getBattery() {
  const level = await BatteryModule.getBatteryLevel();
  console.log(`Battery: ${level}%`);
}
```

> **对比 Flutter Platform Channel**：
> - Flutter 用 `MethodChannel` 双向通信
> - RN 用 `@ReactMethod` + `Promise` 或回调
> - 原理类似：都是 JS/Dart ↔ 原生的桥接机制

---

## 十四、调试与性能优化

### 14.1 调试工具

| 工具 | 用途 |
|------|------|
| **React DevTools** | 检查组件树、Props、State（类似 Flutter DevTools） |
| **Flipper** | 网络请求、布局检查、性能分析（类似 Android Studio Profiler） |
| **Chrome DevTools** | JS 调试、Console、Network（类似 Logcat + Chrome） |
| **Hermes Debugger** | Hermes 引擎专用调试器 |

### 14.2 性能优化要点

```jsx
// 1. 使用 React.memo 避免不必要的重新渲染（类似 Compose 的稳定性检查）
const MemoizedItem = React.memo(({ item }) => {
  return <Text>{item.name}</Text>;
});

// 2. FlatList 优化
<FlatList
  data={items}
  renderItem={renderItem}
  getItemLayout={(data, index) => ({  // 跳过测量，直接计算布局
    length: ITEM_HEIGHT,
    offset: ITEM_HEIGHT * index,
    index,
  })}
  initialNumToRender={10}        // 首屏渲染数量
  windowSize={5}                 // 渲染窗口大小
  removeClippedSubviews={true}   // 移除屏幕外的视图
/>

// 3. 图片优化
<Image
  source={{ uri: 'https://...', cache: 'force-cache' }}
  resizeMode="cover"
/>
```

---

## 十五、核心概念总结与速查表

### 15.1 概念映射速查表

| 概念 | Android (Compose) | Flutter | React Native |
|------|-------------------|---------|--------------|
| 入口 | `setContent {}` | `runApp(MyApp())` | `AppRegistry.registerComponent()` |
| 页面 | `@Composable fun Screen()` | `StatelessWidget` / `StatefulWidget` | `function Screen()` |
| 状态 | `mutableStateOf` + `remember` | `setState()` | `useState` |
| 副作用 | `LaunchedEffect` / `SideEffect` | `initState` / `didChangeDependencies` | `useEffect` |
| 依赖注入 | `compositionLocalOf` | `Provider` / `InheritedWidget` | `Context` / `useContext` |
| 列表 | `LazyColumn` | `ListView.builder` | `FlatList` |
| 导航 | `NavController` | `Navigator` + `Route` | `React Navigation` |
| 网络请求 | `suspend` + Ktor/OkHttp | `Future` + `dio`/`http` | `fetch` / `axios` |
| 动画 | `animate*AsState` | `AnimationController` | `Animated.Value` |
| 生命周期 | `DisposableEffect` | `dispose()` | `useEffect` return |
| 原生桥接 | N/A（本身就是原生） | `MethodChannel` | `NativeModules` / `TurboModules` |
| 状态管理框架 | ViewModel + StateFlow | Bloc / Riverpod / Provider | Redux / Zustand / Jotai |
| 构建工具 | Gradle | Flutter CLI | npm / yarn + Metro Bundler |
| 包管理 | Gradle dependencies | pub.dev | npm / yarn (node_modules) |

### 15.2 从 Compose/Flutter 转 RN 的思维转换

```
1. 语言变化：Kotlin/Dart → JavaScript/TypeScript
   - 动态类型 → 推荐用 TypeScript 获得类型安全
   - 空安全：Kotlin 的 `?` → TS 的 `?.` 和 `??`（空值合并）
   - 数据类：Kotlin `data class` → TS `interface` + 展开运算符 `{ ...obj }`

2. UI 构建变化：函数调用/Widget 构造 → JSX
   - 条件渲染：if/else → `{condition && <Component />}` 或 `{condition ? <A/> : <B/>}`
   - 列表渲染：forEach → `{items.map(item => <Item key={item.id} />)}`

3. 状态管理变化：
   - Compose 的 recomposition → RN 的 re-render
   - Flutter 的 setState → RN 的 setState（几乎一样）
   - 但 RN 的 setState 是**替换式**（`setState({ key: val })` 合并），
     Compose 的 mutableStateOf 是**赋值式**

4. 布局变化：
   - Compose 的 Modifier 链 → RN 的 style 对象
   - Flutter 的 Widget 嵌套 → RN 的 View 嵌套 + style
   - 核心都是 Flexbox，思维转换成本低

5. 异步编程变化：
   - Kotlin 协程（suspend/launch/async）→ JS async/await/Promise
   - Flow → EventEmitter / Observable (rxjs)
   - StateFlow → useState + useEffect 或 Zustand
```

### 15.3 JS/TS 快速对照（Android 工程师常用语法）

```typescript
// 变量
let x = 10;        // 可变（类似 var）
const y = 20;      // 不可重新赋值（类似 val）

// 函数
const add = (a: number, b: number): number => a + b;  // 箭头函数
function add(a: number, b: number): number { return a + b; }  // 普通函数

// 解构赋值（Compose/Flutter 没有的概念）
const { name, age } = user;          // 对象解构
const [first, ...rest] = array;      // 数组解构 + 展开

// 可选链 & 空值合并（类似 Kotlin 的 ?. 和 ?:）
const city = user?.address?.city ?? '未知';  // Kotlin: user?.address?.city ?: "未知"

// Promise（类似 Kotlin 的 Deferred / suspend 函数）
const data = await fetch('/api/data');  // Kotlin: val data = api.getData()

// 展开运算符（类似 Kotlin 的 copy() 或 Flutter 的 copyWith）
const newUser = { ...user, name: '新名字' };  // Kotlin: user.copy(name = "新名字")

// 数组操作（类似 Kotlin 的集合操作符）
const doubled = [1, 2, 3].map(x => x * 2);       // Kotlin: listOf(1,2,3).map { it * 2 }
const evens = [1, 2, 3, 4].filter(x => x % 2 === 0); // Kotlin: listOf(1,2,3,4).filter { it % 2 == 0 }
const sum = [1, 2, 3].reduce((acc, cur) => acc + cur, 0); // Kotlin: listOf(1,2,3).reduce { acc, cur -> acc + cur }
```

---

## 十六、实战：一个完整的计数器页面

```jsx
import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  FlatList, ActivityIndicator, RefreshControl, SafeAreaView
} from 'react-native';

export default function TodoApp() {
  const [todos, setTodos] = useState([]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // 加载数据
  const loadTodos = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch('https://jsonplaceholder.typicode.com/todos?_limit=20');
      const data = await response.json();
      setTodos(data);
    } catch (error) {
      console.error('加载失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadTodos(); }, [loadTodos]);

  // 下拉刷新
  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadTodos();
    setRefreshing(false);
  }, [loadTodos]);

  // 切换完成状态
  const toggleTodo = useCallback((id) => {
    setTodos(prev => prev.map(todo =>
      todo.id === id ? { ...todo, completed: !todo.completed } : todo
    ));
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Todo List</Text>

      {loading && todos.length === 0 ? (
        <ActivityIndicator size="large" style={styles.loader} />
      ) : (
        <FlatList
          data={todos}
          keyExtractor={item => item.id.toString()}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
          renderItem={({ item }) => (
            <TouchableOpacity
              style={[styles.todoItem, item.completed && styles.completed]}
              onPress={() => toggleTodo(item.id)}
            >
              <Text style={[styles.todoText, item.completed && styles.completedText]}>
                {item.completed ? '✓ ' : '○ '}{item.title}
              </Text>
            </TouchableOpacity>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5', paddingTop: 20 },
  title: { fontSize: 28, fontWeight: 'bold', textAlign: 'center', marginBottom: 16 },
  loader: { marginTop: 50 },
  todoItem: {
    backgroundColor: '#fff', padding: 16, marginHorizontal: 16,
    marginBottom: 8, borderRadius: 8, elevation: 2,
  },
  completed: { backgroundColor: '#e8f5e9' },
  todoText: { fontSize: 16 },
  completedText: { textDecorationLine: 'line-through', color: '#999' },
});
```

---

## 附录：推荐学习资源

| 资源 | 链接/说明 |
|------|----------|
| React Native 官方文档 | https://reactnative.dev/docs/getting-started |
| Expo 文档 | https://docs.expo.dev/ |
| React Navigation | https://reactnavigation.org/ |
| React Hooks 指南 | https://react.dev/reference/react |
| TypeScript 入门 | https://www.typescriptlang.org/docs/ |
| Zustand（状态管理） | https://github.com/pmndrs/zustand |
| Reanimated（动画） | https://docs.swmansion.com/react-native-reanimated/ |
