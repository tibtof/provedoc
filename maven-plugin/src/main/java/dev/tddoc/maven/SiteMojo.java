package dev.tddoc.maven;

import dev.tddoc.SiteGen;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates the tddoc article site from doc-tests by invoking
 * {@link SiteGen#main} in-process — the same entry point the CLI and the
 * copy-pasted single file use. Bound to {@code verify} so the suite has
 * passed before the site is built.
 */
@Mojo(name = "site", defaultPhase = LifecyclePhase.VERIFY)
public class SiteMojo extends AbstractMojo {

    /** Project base directory; where {@code tddoc.yml} is looked up. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File basedir;

    /**
     * Directory containing the {@code *DocTest.java} sources. Falls back to
     * the {@code docs} key of {@code tddoc.yml}, then to
     * {@code src/test/java}.
     */
    @Parameter(property = "tddoc.docs")
    private File docs;

    /**
     * Output directory for the generated site. Falls back to the {@code out}
     * key of {@code tddoc.yml}, then to {@code target/site}.
     */
    @Parameter(property = "tddoc.out")
    private File out;

    /** Site name shown in the header. */
    @Parameter(property = "tddoc.name")
    private String name;

    /** Tagline under the site name. */
    @Parameter(property = "tddoc.tagline")
    private String tagline;

    /** Repository URL for header and footer links. */
    @Parameter(property = "tddoc.repo")
    private String repo;

    /** One-letter favicon glyph. */
    @Parameter(property = "tddoc.glyph")
    private String glyph;

    /** Install snippet for the landing page; {@code {version}} is substituted. */
    @Parameter(property = "tddoc.install")
    private String install;

    /** Base URL for "edit this page" links. */
    @Parameter(property = "tddoc.editBase")
    private String editBase;

    /** Javadoc directory folded in under {@code /api/}. */
    @Parameter(property = "tddoc.javadoc")
    private File javadoc;

    /** Version rendered on the site; defaults to the project version. */
    @Parameter(property = "tddoc.version", defaultValue = "${project.version}")
    private String version;

    /** Path prefix for versioned deploys. */
    @Parameter(property = "tddoc.prefix")
    private String prefix;

    /** Version-selector label for this build. */
    @Parameter(property = "tddoc.channel")
    private String channel;

    @Override
    public void execute() throws MojoExecutionException {
        // Zero-config path: tddoc.yml in the project root supplies anything not
        // set in <configuration>; explicit configuration always wins.
        Map<String, String> config = Map.of();
        File yml = new File(basedir, "tddoc.yml");
        if (yml.isFile()) {
            try {
                config = SiteGen.readConfig(yml.toPath());
            } catch (IOException e) {
                throw new MojoExecutionException("cannot read " + yml, e);
            }
        }
        if (docs == null) {
            docs = new File(basedir, config.getOrDefault("docs", "src/test/java"));
        }
        if (out == null) {
            out = config.containsKey("out")
                    ? new File(basedir, config.get("out"))
                    : new File(basedir, "target/site");
        }
        name = orConfig(name, config, "name");
        tagline = orConfig(tagline, config, "tagline");
        repo = orConfig(repo, config, "repo");
        glyph = orConfig(glyph, config, "glyph");
        install = orConfig(install, config, "install");
        editBase = orConfig(editBase, config, "editBase");
        prefix = orConfig(prefix, config, "prefix");
        channel = orConfig(channel, config, "channel");
        if (javadoc == null && config.containsKey("javadoc")) {
            javadoc = new File(basedir, config.get("javadoc"));
        }

        List<String> args = new ArrayList<>();
        args.add("--docs");
        args.add(docs.getPath());
        args.add("--out");
        args.add(out.getPath());
        addIf(args, "--name", name);
        addIf(args, "--tagline", tagline);
        addIf(args, "--repo", repo);
        addIf(args, "--glyph", glyph);
        addIf(args, "--install", install);
        addIf(args, "--editBase", editBase);
        addIf(args, "--version", version);
        addIf(args, "--prefix", prefix);
        addIf(args, "--channel", channel);
        if (javadoc != null) {
            args.add("--javadoc");
            args.add(javadoc.getPath());
        }
        try {
            SiteGen.main(args.toArray(String[]::new));
        } catch (Exception e) {
            throw new MojoExecutionException("tddoc site generation failed", e);
        }
        getLog().info("tddoc site: " + out);
    }

    private static void addIf(List<String> args, String flag, String value) {
        if (value != null && !value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    private static String orConfig(String explicit, Map<String, String> config, String key) {
        return explicit != null ? explicit : config.get(key);
    }
}
