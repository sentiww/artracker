{
  description = "AR Tracker development flake";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.05";

  outputs = { self, nixpkgs }:
  let
    system = "x86_64-linux";
    pkgs = import nixpkgs {
      inherit system;
      config = {
        allowUnfree = true;
        android_sdk.accept_license = true;
      };
    };

    android = pkgs.androidenv.composeAndroidPackages {
      platformToolsVersion = "35.0.2";
      buildToolsVersions = [ "35.0.0" ];
      platformVersions = [ "35" ];
      includeEmulator = false;
      includeNDK = false;
    };
  in {
    devShells.${system}.default = pkgs.mkShell {
      packages = with pkgs; [
        jdk17
        gradle
        androidStudioPackages.stable
        android.androidsdk
        android.platform-tools
        git
        unzip
      ];

      ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
      ANDROID_HOME     = "${android.androidsdk}/libexec/android-sdk";
      JAVA_HOME        = "${pkgs.jdk17}";

      shellHook = ''
        export ANDROID_USER_HOME="$PWD/.android"
        export GRADLE_USER_HOME="$PWD/.gradle"

        export ANDROID_SDK_ROOT="$PWD/.android-sdk"
        export ANDROID_HOME="$PWD/.android-sdk"

        mkdir -p "$ANDROID_SDK_ROOT"
      '';
    };

    apps.${system}.android-studio = {
      type = "app";
      program =
        let
          launcher = pkgs.writeShellScriptBin "open-android-studio" ''
            set -euo pipefail

            if [ ! -f "gradlew" ]; then
              echo "Please run this command from the project root (where gradlew lives)." >&2
              exit 1
            fi

            export ANDROID_USER_HOME="''${ANDROID_USER_HOME:-$PWD/.android}"
            export ANDROID_SDK_ROOT="''${ANDROID_SDK_ROOT:-${android.androidsdk}/libexec/android-sdk}"
            export ANDROID_HOME="$ANDROID_SDK_ROOT"
            export JAVA_HOME="${pkgs.jdk17}"

            exec ${pkgs.androidStudioPackages.stable}/bin/android-studio "$PWD"
          '';
        in
        "${launcher}/bin/open-android-studio";
    };
  };
}
