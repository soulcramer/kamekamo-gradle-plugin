plugins {
  kotlin("jvm")
}

if (hasProperty("KameKamoRepositoryUrl")) {
  apply(plugin = "com.vanniktech.maven.publish")
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  compileOnly(libs.agp)
  implementation(libs.guava)
}
