suite = {
  "mxversion" : "5.103.0",
  "name" : "megaguards",
  "versionConflictResolution" : "latest",
  "imports" : {
    "suites" : [
            {
               "name" : "truffle",
               "subdir" : True,
               "version" : "0a2c9e991ef1cde128d6f57fa33338234331e63c",
               "urls" : [
                    {"url" : "https://github.com/graalvm/graal", "kind" : "git"},
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
            {
               "name" : "sdk",
               "subdir" : True,
               "version" : "0a2c9e991ef1cde128d6f57fa33338234331e63c",
               "urls" : [
                    {"url" : "https://github.com/graalvm/graal", "kind" : "git"},
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },

        ],
   },

  "defaultLicense" : "BSD-2-Clause",

  "libraries" : {

    # ------------- Libraries -------------

    "JOCL" : {
      "urls" : [
        "https://www.dropbox.com/s/pbqpfgcgdcet8vk/jocl-2.0.1-SNAPSHOT.jar?dl=1",
        ],
      "sha1" : "79e3a18a5cc5a6532ef86df8cc6e9645998b8ecc",
    },

    "JAVACPP" : {
      "urls" : [
        "https://search.maven.org/remotecontent?filepath=org/bytedeco/javacpp/1.3.1/javacpp-1.3.1.jar",
        ],
      "sha1" : "f0900cdbfdafa3d12575ddb91ddb3ca19e16777a",
    },

  },

  "projects" : {

    # ------------- MegaGuards -------------

    "edu.uci.megaguards" : {
      "subDir" : "megaguards",
      "sourceDirs" : ["src"],
      "dependencies" : [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_DSL_PROCESSOR",
                "sdk:GRAAL_SDK",
                "JOCL",
                "JAVACPP",
                ],
      "checkstyle" : "edu.uci.megaguards",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["truffle:TRUFFLE_DSL_PROCESSOR"],
      "workingSets" : "Truffle",
    },

  },

  "licenses" : {
    "BSD-2-Clause" : {
      "name" : "FreeBSD License",
      "url" : "http://opensource.org/licenses/BSD-2-Clause",
    },
  },

  "distributions" : {
    "MEGAGUARDS" : {
      "path" : "megaguards.jar",
      "dependencies" : [
        "edu.uci.megaguards",
      ],
      "distDependencies" : [
        "truffle:TRUFFLE_API",
        "truffle:TRUFFLE_DSL_PROCESSOR",
        ],
      "exclude" : [
        "JOCL",
        "JAVACPP",
        ],
      "sourcesPath" : "megaguards.src.zip",
    },

  },
}
