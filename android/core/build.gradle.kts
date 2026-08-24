plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.json:json:20240303")
    // useJUnitPlatform() tek başına yeterli değil: motor yoksa Gradle
    // testleri sessizce "no tests found" diye atlar.
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

/**
 * `shared/golden.json` tek doğru kaynaktır. Test kaynaklarına elle kopya
 * bırakmak yerine her derlemede oradan çekiyoruz; aksi halde kopyalar
 * sessizce ayrışır ve testin varlık sebebi ortadan kalkar.
 */
val syncGolden by tasks.registering(Copy::class) {
    from(rootProject.file("../shared/golden.json"))
    into(layout.projectDirectory.dir("src/test/resources"))
}

tasks.named("processTestResources") { dependsOn(syncGolden) }

tasks.test { useJUnitPlatform() }
