package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class MainWorkflow {

    static final String PROJECT_ROOT = System.getProperty("user.dir");
    static final String RESOURCES_PATH = PROJECT_ROOT + File.separator + "src" + File.separator + "main" + File.separator + "resources";

    // Fichiers intermédiaires constants (on les écrase à chaque fois, c'est plus simple)
    static final String FILE_RCFT     = RESOURCES_PATH + File.separator + "sortie.rcft";
    static final String SCRIPT_PYTHON = "llm.py";
    static final String PLAN_JSON     = RESOURCES_PATH + File.separator + "plan_amelioration.json";
    static final String PYTHON_CMD    = "python3";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        // --- GESTION DE L'ARGUMENT D'ENTRÉE ---
        String inputFileName;
        if (args.length > 0) {
            inputFileName = args[0]; // L'utilisateur a donné un nom
        } else {
            inputFileName = "transport.ecore"; // Valeur par défaut
            System.out.println("[INFO] Aucun fichier spécifié. Utilisation par défaut : " + inputFileName);
        }

        // On construit le chemin complet du fichier source
        String fullInputPath = RESOURCES_PATH + File.separator + inputFileName;

        // On vérifie que le fichier existe avant de commencer
        if (!new File(fullInputPath).exists()) {
            System.err.println("[ERREUR CRITIQUE] Le fichier n'existe pas : " + fullInputPath);
            return;
        }

        System.out.println("==========================================");
        System.out.println("   DÉMARRAGE DU PIPELINE IDM-RCA-LLM");
        System.out.println("   Fichier cible : " + inputFileName);
        System.out.println("==========================================\n");

        // --- ETAPE 1 : EXTRACTION (JAVA) ---
        System.out.println("[JAVA] 1. Extraction du modèle...");
        // On passe le chemin dynamique ici
        ModelExtractor.runExtraction(fullInputPath, FILE_RCFT);

        // --- ETAPE 2 : ANALYSE & IA (PYTHON) ---
        // Le Python lit toujours "sortie.rcft" (généré juste au-dessus), donc rien à changer ici
        runPythonScript();

        // --- ETAPE 3 : RECONSTRUCTION (JAVA) ---
        File jsonFile = new File(PLAN_JSON);
        if (jsonFile.exists() && jsonFile.length() > 0) {
            System.out.println("\n[JAVA] 3. Lancement du Refactoring...");

            // IMPORTANT : On transmet le chemin du fichier à refactorer à l'étape 3 !
            // On passe un tableau d'arguments contenant le chemin complet
            RefactoringAuto.main(new String[]{ fullInputPath });

        } else {
            System.err.println("\n[ERREUR] Le fichier JSON n'a pas été généré.");
        }

        long end = System.currentTimeMillis();
        System.out.println("\n==========================================");
        System.out.println("   WORKFLOW TERMINÉ (" + (end - start) + "ms)");
        System.out.println("==========================================");
    }

    private static void runPythonScript() {
        System.out.println("\n[SYSTEM] 2. Lancement du script Python...");
        try {
            ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, SCRIPT_PYTHON);
            pb.directory(new File(RESOURCES_PATH)); // Exécution DANS resources
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("   [PYTHON] " + line);
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("   -> Impossible de lancer Python : " + e.getMessage());
        }
    }
}