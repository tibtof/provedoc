package dev.tddoc.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the {@code tddocSite} task; each property mirrors the
 * SiteGen CLI flag of the same name so the plugin stays a thin wrapper.
 */
public abstract class TddocExtension {

    /** Directory containing the {@code *DocTest.java} sources. */
    public abstract DirectoryProperty getDocs();

    /** Output directory for the generated site. */
    public abstract DirectoryProperty getOut();

    /** Site name shown in the header ({@code --name}). */
    public abstract Property<String> getName();

    /** Tagline under the site name ({@code --tagline}). */
    public abstract Property<String> getTagline();

    /** Repository URL for header and footer links ({@code --repo}). */
    public abstract Property<String> getRepo();

    /** One-letter favicon glyph ({@code --glyph}). */
    public abstract Property<String> getGlyph();

    /** Install snippet for the landing page; {@code {version}} is substituted ({@code --install}). */
    public abstract Property<String> getInstall();

    /** Base URL for "edit this page" links ({@code --editBase}). */
    public abstract Property<String> getEditBase();

    /** Javadoc directory folded in under {@code /api/} ({@code --javadoc}). */
    public abstract DirectoryProperty getJavadoc();

    /** Version rendered on the site ({@code --version}); defaults to the project version. */
    public abstract Property<String> getVersion();

    /** Path prefix for versioned deploys ({@code --prefix}). */
    public abstract Property<String> getPrefix();

    /** Version-selector label for this build ({@code --channel}). */
    public abstract Property<String> getChannel();
}
