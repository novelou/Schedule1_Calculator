# Schedule1 Calculator

## 概要

Schedule1 Calculatorは、Compose Desktopを使用して開発されたKotlinアプリケーションです。様々な素材を組み合わせて特定の効果を生成するシミュレーション・検索ツールです。アプリケーションは2つの主要な機能を提供します：

1. **効果検索機能**: 目標とする効果を選択し、その効果を生成するための素材の組み合わせパスを検索
2. **シミュレーション機能**: 選択した素材の組み合わせから実際に発現する効果を確認

## 技術仕様

- **言語**: Kotlin 2.0.20
- **UI フレームワーク**: Compose Desktop 1.7.1
- **ビルドツール**: Gradle
- **対象プラットフォーム**: Desktop (AppImage形式)

## プロジェクト構造

```
src/main/kotlin/
├── Main.kt                           # メインアプリケーション（UI）
├── datas/                            # データクラス
│   ├── EffectDependency.kt          # 効果の依存関係
│   ├── Material.kt                  # 素材データ
│   ├── RecipeRequirement.kt         # レシピ要件
│   ├── RecipeResult.kt              # レシピ結果
│   └── State.kt                     # 状態管理
├── resources/                        # リソース定義
│   ├── idToEffectName.kt           # 効果IDと名前のマッピング
│   └── resources.kt                # 素材とレシピの定義
└── services/                        # ビジネスロジック
    ├── Auxiliarys.kt               # ユーティリティ関数
    ├── buildDependencyTree.kt      # 依存関係ツリー構築
    ├── findEffectByRequirements.kt # 要件に基づく効果検索
    ├── findPathsToTargetEffectsViaSimulation.kt # シミュレーション検索
    ├── getEffectByPath.kt          # パスから効果取得
    └── materialToEffectId.kt       # 素材から効果ID変換
```

## 主要機能

### 1. 効果検索機能

**目的**: 指定した効果を生成するための素材の組み合わせパスを検索

**機能詳細**:
- 35種類の効果から複数選択可能
- 最大結果数を1-100の範囲で設定可能
- 非同期処理によるバックグラウンド検索
- 15秒のタイムアウト制限
- 検索結果はパターン別に表示

**利用可能な効果**:
- Shrinking, Zombifying, Cyclopean, Anti-Gravity
- Long-Faced, Electrifying, Glowing, Tropic-Thunder
- Thought-Provoking, Jenerising, Bright-Eyed, Spicy
- Foggy, Slippery, Athletic, Balding, Calorie-Dense
- Sedating, Sneaky, Energizing, Gingeritis, Euphoric
- Focused, Refreshing, Munchies, Calming, Toxic
- Smelly, Paranoia, Schizophrenic, Laxative
- Disorienting, Explosive, Seizure-Inducing

### 2. シミュレーション機能

**目的**: 選択した素材の組み合わせから実際に発現する効果を確認

**機能詳細**:
- 16種類の基本素材から選択
- リアルタイムでの素材選択と効果確認
- 選択した素材リストの表示とリセット機能
- 発現した効果の即座な表示

**利用可能な基本素材**:
- Cuke, Banana, Paracetamol, Donut, Viagra
- Mouth Wash, Flu Medicine, Gasoline, Energy Drink
- Motor Oil, Mega Bean, Chili, Battery, Iodine
- Addy, Horse Samen

## データモデル

### Material（素材）
```kotlin
data class Material(
    val name: String,        // 素材名
    val effectId: Int        // 効果ID
)
```

### RecipeRequirement（レシピ要件）
```kotlin
data class RecipeRequirement(
    val effectId: Int,                    // 生成される効果ID
    val requiredMaterial: String,         // 必要な素材
    val requiredSubEffectId: Int,         // 必要なサブ効果ID
    val excludeEffectId: Int? = null,     // 除外する効果ID（オプション）
    val includeEffectId: Int? = null      // 含める必要がある効果ID（オプション）
)
```

### EffectDependency（効果依存関係）
```kotlin
data class EffectDependency(
    val effectId: Int,                           // 効果ID
    val requiredSubEffects: MutableList<Int>,    // 必要なサブ効果リスト
    val requiredMaterials: MutableList<String>   // 必要な素材リスト
)
```

## アルゴリズム

### 効果検索アルゴリズム
1. 幅優先探索（BFS）を使用して素材の組み合わせを探索
2. 各パスに対して`getEffectByPath`関数で効果を計算
3. 目標効果がすべて含まれるパスを結果として収集
4. 最大結果数に達するか、探索が完了するまで継続

### 効果計算アルゴリズム
1. 最初の素材の効果を初期効果として設定
2. 各追加素材について：
   - 既存効果と素材の組み合わせでレシピを検索
   - 条件（include/exclude）を満たすレシピがあれば効果を更新
   - 素材自体の効果も追加（重複しない場合）

## レシピシステム

アプリケーションには148個のレシピが定義されており、各レシピは以下の条件で動作します：

- **基本条件**: 特定の素材とサブ効果の組み合わせ
- **includeEffectId**: 指定された効果が既に存在する場合のみ適用
- **excludeEffectId**: 指定された効果が存在しない場合のみ適用

## ビルドと実行

### 前提条件
- Java 11以上
- Gradle 7.0以上

### ビルド
```bash
./gradlew build
```

### 実行
```bash
./gradlew run
```

### 配布用パッケージ作成
```bash
./gradlew packageAppImage
```

## 使用方法

1. **効果検索**:
   - 「検索」タブを選択
   - 目標とする効果にチェックを入れる
   - 最大結果数を設定
   - 「検索」ボタンをクリック

2. **シミュレーション**:
   - 「シミュレーション」タブを選択
   - 基本素材ボタンをクリックして素材を選択
   - 「効果を確認」ボタンで結果を表示
   - 「リストをリセット」で選択をクリア

## 注意事項

- 検索処理は非同期で実行され、最大15秒でタイムアウトします
- 複雑な組み合わせの検索には時間がかかる場合があります
- 素材の組み合わせは最大10個までに制限されています
- 一部のレシピには相互排他的な条件が設定されています

## ライセンス

このプロジェクトのライセンス情報については、プロジェクトのルートディレクトリを確認してください。