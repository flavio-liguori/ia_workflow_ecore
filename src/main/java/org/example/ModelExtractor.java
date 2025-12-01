package org.example;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ModelExtractor {

    public static void runExtraction(String inputEcorePath, String outputRcftPath) {
        System.out.println("[JAVA] 1. Extraction des données vers RCFT...");

        // Init EMF
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        ResourceSet rs = new ResourceSetImpl();

        try {
            Resource resource = rs.getResource(URI.createFileURI(inputEcorePath), true);
            EPackage rootPackage = (EPackage) resource.getContents().get(0);

            List<EClass> classes = new ArrayList<>();
            Set<String> properties = new LinkedHashSet<>();

            // Analyse
            for (EClassifier classifier : rootPackage.getEClassifiers()) {
                if (classifier instanceof EClass) {
                    EClass eClass = (EClass) classifier;
                    classes.add(eClass);
                    for (EAttribute attr : eClass.getEAttributes()) properties.add(getSig(attr));
                    for (EOperation op : eClass.getEOperations()) properties.add(getSig(op));
                }
            }

            // Ecriture RCFT
            try (FileWriter writer = new FileWriter(outputRcftPath)) {
                writer.write("FormalContext ModeleUML\n| |");
                for (String p : properties) writer.write(" " + p + " |");
                writer.write("\n");

                for (EClass c : classes) {
                    writer.write("| " + c.getName() + " |");
                    for (String p : properties) {
                        writer.write(hasProp(c, p) ? " X |" : " |");
                    }
                    writer.write("\n");
                }
            }
            System.out.println("   -> Fichier généré : " + outputRcftPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getSig(ENamedElement e) {
        if (e instanceof EAttribute) return e.getName() + ":" + ((EAttribute)e).getEType().getName();
        if (e instanceof EOperation) return e.getName() + "()";
        return e.getName();
    }

    private static boolean hasProp(EClass c, String sig) {
        for (EAttribute a : c.getEAttributes()) if (getSig(a).equals(sig)) return true;
        for (EOperation o : c.getEOperations()) if (getSig(o).equals(sig)) return true;
        return false;
    }
}