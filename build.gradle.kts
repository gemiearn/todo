// Root build file. Plugins are declared here (without applying) so that the
// version catalog stays the single place that defines plugin versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
