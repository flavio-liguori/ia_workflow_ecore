package org.example;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class RefactoringAuto {

    public static void main(String[] args) {
        // 1. Initialisation EMF
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new XMIResourceFactoryImpl());
        ResourceSet rs = new ResourceSetImpl();

        // --- GESTION DYNAMIQUE DU FICHIER D'ENTRÉE ---
        String inputModelPath;
        if (args.length > 0) {
            // Cas 1 : Appelé par MainWorkflow avec un chemin précis
            inputModelPath = args[0];
        } else {
            // Cas 2 : Test manuel (Fallback) - Chemin relatif standard
            String projectRoot = System.getProperty("user.dir");
            inputModelPath = projectRoot + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "transport.ecore";
            System.out.println("[INFO] Aucun argument. Utilisation par défaut : " + inputModelPath);
        }

        // 2. Chargement du Modèle Initial
        Resource resource;
        try {
            resource = rs.getResource(URI.createFileURI(inputModelPath), true);
        } catch (Exception e) {
            System.err.println("[ERREUR] Impossible de lire le fichier Ecore : " + inputModelPath);
            e.printStackTrace();
            return;
        }

        EPackage rootPackage = (EPackage) resource.getContents().get(0);
        System.out.println("Modèle chargé : " + rootPackage.getName());

        // 3. Lecture du Plan d'Amélioration (JSON)
        // On construit le chemin vers le JSON dans src/main/resources
        String projectRoot = System.getProperty("user.dir");
        String jsonPath = projectRoot + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "plan_amelioration.json";

        List<RefactoringAction> actions = loadImprovementPlan(jsonPath);

        if (actions.isEmpty()) {
            System.out.println("Aucune action d'amélioration à effectuer (Liste vide ou fichier JSON non trouvé).");
            return;
        }

        // 4. Exécution des transformations
        for (RefactoringAction action : actions) {
            applyRefactoring(rootPackage, action);
        }

        // 5. Sauvegarde
        // On génère le nom de sortie basé sur l'entrée (ex: transport.ecore -> transport_refactore.ecore)
        String outputModelPath = inputModelPath.replace(".ecore", "_refactore.ecore");

        resource.setURI(URI.createFileURI(outputModelPath));
        try {
            resource.save(Collections.emptyMap());
            System.out.println("\n[SUCCÈS] Modèle amélioré sauvegardé sous : " + outputModelPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- LOGIQUE DE TRANSFORMATION ---
    private static void applyRefactoring(EPackage pkg, RefactoringAction action) {
        System.out.println("\n--- Application : " + action.type + " -> " + action.conceptName + " ---");

        // Vérification de sécurité : est-ce que les classes enfants existent ?
        if (action.concernedClasses.isEmpty()) return;

        // On récupère le premier enfant pour piocher les attributs
        EClassifier classifier = pkg.getEClassifier(action.concernedClasses.get(0));
        if (classifier == null || !(classifier instanceof EClass)) {
            System.err.println("[ERREUR] La classe enfant '" + action.concernedClasses.get(0) + "' n'existe pas dans le modèle.");
            return;
        }
        EClass firstChild = (EClass) classifier;

        // A. Création du nouveau concept (Classe ou Interface)
        EClass newConcept = EcoreFactory.eINSTANCE.createEClass();
        newConcept.setName(action.conceptName);
        newConcept.setAbstract(true);

        if ("INTERFACE".equals(action.type)) {
            newConcept.setInterface(true);
            System.out.println(" + Création de l'Interface : " + action.conceptName);
        } else {
            newConcept.setInterface(false);
            System.out.println(" + Création de la Classe Abstraite : " + action.conceptName);
        }

        pkg.getEClassifiers().add(newConcept);

        // B. Gestion des éléments à remonter
        for (String featureName : action.elementsToMove) {
            // Nettoyage (ex: "nom" ou "manger()")
            String cleanName = featureName.split(":")[0].replace("()", "").trim();

            EStructuralFeature attr = firstChild.getEStructuralFeature(cleanName);
            EOperation op = findOperation(firstChild, cleanName);

            boolean isInterface = newConcept.isInterface();

            if (op != null) {
                newConcept.getEOperations().add(op);
                System.out.println("   ^ Opération déplacée : " + cleanName);
            } else if (attr != null && !isInterface) {
                newConcept.getEStructuralFeatures().add(attr);
                System.out.println("   ^ Attribut déplacé : " + cleanName);
            }
        }

        // C. Application de l'héritage/implémentation aux enfants
        for (String childName : action.concernedClasses) {
            EClass childClass = (EClass) pkg.getEClassifier(childName);
            if (childClass == null) continue;

            // Ajout du parent (SuperType)
            childClass.getESuperTypes().add(newConcept);
            System.out.println("   > Lien ajouté : " + childName + " -> " + action.conceptName);

            // Nettoyage des doublons dans les autres enfants
            if (!childClass.getName().equals(firstChild.getName())) {
                for (String featureName : action.elementsToMove) {
                    String cleanName = featureName.split(":")[0].replace("()", "").trim();

                    if (!newConcept.isInterface()) {
                        EStructuralFeature attrToRemove = childClass.getEStructuralFeature(cleanName);
                        if (attrToRemove != null) childClass.getEStructuralFeatures().remove(attrToRemove);
                    }

                    EOperation opToRemove = findOperation(childClass, cleanName);
                    if (opToRemove != null) childClass.getEOperations().remove(opToRemove);
                }
            }
        }
    }

    private static EOperation findOperation(EClass cls, String name) {
        for (EOperation op : cls.getEOperations()) {
            if (op.getName().equals(name)) return op;
        }
        return null;
    }

    // --- PARSER JSON ---
    static class RefactoringAction {
        String type;
        String conceptName;
        List<String> concernedClasses = new ArrayList<>();
        List<String> elementsToMove = new ArrayList<>();
    }

    private static List<RefactoringAction> loadImprovementPlan(String path) {
        List<RefactoringAction> actions = new ArrayList<>();
        File file = new File(path);

        if (!file.exists()) {
            System.err.println("[ERREUR] Fichier JSON introuvable : " + path);
            return actions;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) jsonBuilder.append(line);

            String json = jsonBuilder.toString().trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
            }

            String[] objects = json.split("},");

            for (String obj : objects) {
                if (obj.trim().isEmpty()) continue;
                RefactoringAction action = new RefactoringAction();

                action.type = extractValue(obj, "\"type\":");
                action.conceptName = extractValue(obj, "\"concept_name\":");
                action.concernedClasses.addAll(extractList(obj, "\"classes_concernees\":"));
                action.elementsToMove.addAll(extractList(obj, "\"elements_remontes\":"));

                if (action.type != null && action.conceptName != null) {
                    actions.add(action);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lecture JSON : " + e.getMessage());
        }
        return actions;
    }

    private static String extractValue(String source, String key) {
        int idx = source.indexOf(key);
        if (idx == -1) return null;
        int start = source.indexOf("\"", idx + key.length()) + 1;
        int end = source.indexOf("\"", start);
        return source.substring(start, end);
    }

    private static List<String> extractList(String source, String key) {
        List<String> list = new ArrayList<>();
        int idx = source.indexOf(key);
        if (idx == -1) return list;
        int startBracket = source.indexOf("[", idx);
        int endBracket = source.indexOf("]", startBracket);
        if (startBracket == -1 || endBracket == -1) return list;

        String content = source.substring(startBracket + 1, endBracket);
        String[] items = content.split(",");
        for (String item : items) {
            String clean = item.trim().replace("\"", "").replace("\n", "");
            if (!clean.isEmpty()) list.add(clean);
        }
        return list;
    }
}