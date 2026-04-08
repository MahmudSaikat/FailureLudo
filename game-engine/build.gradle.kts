import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.json:json:20240303")
    testImplementation(libs.junit)
}

private fun resolvePathArg(raw: String?, defaultRelativePath: String): String {
    val candidate = raw?.takeIf { it.isNotBlank() } ?: defaultRelativePath
    val file = File(candidate)
    return if (file.isAbsolute) file.absolutePath else rootProject.file(candidate).absolutePath
}

tasks.register<JavaExec>("generateSelfPlayDataset") {
    group = "training"
    description = "Generate self-play training dataset JSONL using the engine simulator."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.failureludo.engine.SelfPlayDatasetCliKt")
    workingDir = rootProject.projectDir

    val buildOutput = layout.buildDirectory.file("self-play/self_play_dataset.jsonl").get().asFile.absolutePath
    val outputProperty = project.findProperty("output")?.toString()
    val output = if (outputProperty.isNullOrBlank()) {
        buildOutput
    } else {
        val raw = File(outputProperty)
        if (raw.isAbsolute) {
            raw.absolutePath
        } else {
            rootProject.file(outputProperty).absolutePath
        }
    }

    args(
        "--episodes", (project.findProperty("episodes")?.toString() ?: "10000"),
        "--maxPly", (project.findProperty("maxPly")?.toString() ?: "3000"),
        "--output", output,
        "--mode", (project.findProperty("mode")?.toString() ?: "FREE_FOR_ALL"),
        "--policy", (project.findProperty("policy")?.toString() ?: "heuristic"),
        "--activeColors", (project.findProperty("activeColors")?.toString() ?: "RED,BLUE,YELLOW,GREEN"),
        "--explorationEpsilon", (project.findProperty("explorationEpsilon")?.toString() ?: "0.0")
    )

    project.findProperty("seed")?.toString()?.let { seed ->
        args("--seed", seed)
    }
}

tasks.register<JavaExec>("evaluatePolicyArena") {
    group = "training"
    description = "Evaluate challenger and baseline self-play policies and write a summary JSON."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.failureludo.engine.PolicyPromotionGateCliKt")
    workingDir = rootProject.projectDir

    val summaryOutput = resolvePathArg(
        raw = project.findProperty("summaryOutput")?.toString(),
        defaultRelativePath = "game-engine/build/self-play/policy_arena_summary.json"
    )

    args(
        "--episodes", (project.findProperty("episodes")?.toString() ?: "400"),
        "--maxPly", (project.findProperty("maxPly")?.toString() ?: "3000"),
        "--mode", (project.findProperty("mode")?.toString() ?: "FREE_FOR_ALL"),
        "--activeColors", (project.findProperty("activeColors")?.toString() ?: "RED,BLUE,YELLOW,GREEN"),
        "--challengerPolicy", (project.findProperty("challengerPolicy")?.toString() ?: "heuristic"),
        "--baselinePolicy", (project.findProperty("baselinePolicy")?.toString() ?: "random"),
        "--promotionThreshold", (project.findProperty("promotionThreshold")?.toString() ?: "0.55"),
        "--minimumDecidedGames", (project.findProperty("minimumDecidedGames")?.toString() ?: "100"),
        "--summaryOutput", summaryOutput,
        "--failOnGate", "false"
    )

    project.findProperty("seed")?.toString()?.let { args("--seed", it) }
    project.findProperty("seedList")?.toString()?.let { args("--seedList", it) }
    project.findProperty("maxP95DecisionMs")?.toString()?.let { args("--maxP95DecisionMs", it) }
    project.findProperty("challengerModel")?.toString()?.let {
        args("--challengerModel", resolvePathArg(it, it))
    }
    project.findProperty("baselineModel")?.toString()?.let {
        args("--baselineModel", resolvePathArg(it, it))
    }
}

tasks.register<JavaExec>("runPolicyPromotionGate") {
    group = "training"
    description = "Run challenger-vs-baseline evaluation and fail when promotion threshold is not met."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.failureludo.engine.PolicyPromotionGateCliKt")
    workingDir = rootProject.projectDir

    val summaryOutput = resolvePathArg(
        raw = project.findProperty("summaryOutput")?.toString(),
        defaultRelativePath = "game-engine/build/self-play/policy_promotion_gate_summary.json"
    )

    args(
        "--episodes", (project.findProperty("episodes")?.toString() ?: "400"),
        "--maxPly", (project.findProperty("maxPly")?.toString() ?: "3000"),
        "--mode", (project.findProperty("mode")?.toString() ?: "FREE_FOR_ALL"),
        "--activeColors", (project.findProperty("activeColors")?.toString() ?: "RED,BLUE,YELLOW,GREEN"),
        "--challengerPolicy", (project.findProperty("challengerPolicy")?.toString() ?: "heuristic"),
        "--baselinePolicy", (project.findProperty("baselinePolicy")?.toString() ?: "random"),
        "--promotionThreshold", (project.findProperty("promotionThreshold")?.toString() ?: "0.55"),
        "--minimumDecidedGames", (project.findProperty("minimumDecidedGames")?.toString() ?: "100"),
        "--summaryOutput", summaryOutput,
        "--failOnGate", "true"
    )

    project.findProperty("seed")?.toString()?.let { args("--seed", it) }
    project.findProperty("seedList")?.toString()?.let { args("--seedList", it) }
    project.findProperty("maxP95DecisionMs")?.toString()?.let { args("--maxP95DecisionMs", it) }
    project.findProperty("challengerModel")?.toString()?.let {
        args("--challengerModel", resolvePathArg(it, it))
    }
    project.findProperty("baselineModel")?.toString()?.let {
        args("--baselineModel", resolvePathArg(it, it))
    }
    project.findProperty("promoteTo")?.toString()?.let {
        args("--promoteTo", resolvePathArg(it, it))
    }
}
