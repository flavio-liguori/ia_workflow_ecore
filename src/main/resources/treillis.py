from concepts import Context

def generate_lattice_from_rcft(filepath):
    """
    Lit un fichier RCFT et génère le treillis en construisant 
    explicitement la matrice booléenne.
    """
    objects = []
    properties = []
    bools_matrix = [] # La matrice finale (Liste de Listes de booléens)

    print(f"--- Lecture du fichier : {filepath} ---")
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = [line.strip() for line in f if line.strip()]
    except FileNotFoundError:
        print(f"Erreur : Le fichier {filepath} n'existe pas.")
        return None

    # 1. Détection des propriétés (En-tête)
    header_index = -1
    for i, line in enumerate(lines):
        # On cherche la ligne qui contient les noms de colonnes
        if line.startswith('|'):
            parts = [p.strip() for p in line.split('|')]
            # On considère que c'est l'en-tête si on a plusieurs éléments
            # parts[0] est vide, parts[1] est vide (coin haut gauche), parts[2...] sont les props
            clean_props = [p for p in parts if p] 
            if len(clean_props) > 0:
                properties = clean_props
                header_index = i
                break
    
    if header_index == -1:
        print("Erreur : Format RCFT invalide (Pas d'en-tête trouvé).")
        return None

    print(f"Propriétés trouvées ({len(properties)}) : {properties}")

    # 2. Lecture des objets et construction de la matrice
    for line in lines[header_index + 1:]:
        if not line.startswith('|'): continue
        
        parts = line.split('|')
        # parts[0] est vide avant le premier pipe
        # parts[1] devrait être le nom de l'objet
        if len(parts) < 2: continue
        
        obj_name = parts[1].strip()
        if not obj_name: continue # Ligne séparatrice ou vide
        
        objects.append(obj_name)
        
        # Construction de la ligne booléenne pour cet objet
        # On regarde les cellules suivantes correspondant aux propriétés
        row_bools = []
        
        # Les valeurs commencent à l'index 2 du split (après le vide et le nom)
        # On doit s'assurer de ne pas dépasser le nombre de propriétés
        values = parts[2:-1] # On exclut le dernier pipe vide souvent présent
        
        for i in range(len(properties)):
            is_present = False
            # On vérifie si on a une croix 'x' ou 'X' à cet index
            if i < len(values):
                cell_content = values[i].strip().lower()
                if cell_content == 'x':
                    is_present = True
            
            row_bools.append(is_present)
            
        bools_matrix.append(row_bools)

    # Vérification de cohérence avant création
    if len(objects) != len(bools_matrix):
        print("Erreur : Nombre d'objets et de lignes de matrice incohérent.")
        return None

    print(f"Objets trouvés ({len(objects)}) : {objects}")

    # 3. Création du Contexte (CORRECTION ICI)
    # On passe la matrice booléenne directement
    try:
        context = Context(objects, properties, bools_matrix)
    except ValueError as e:
        print(f"Erreur fatale lors de la création du contexte : {e}")
        print(f"Debug - Objets: {len(objects)}, Props: {len(properties)}, Matrice: {len(bools_matrix)}x{len(bools_matrix[0]) if bools_matrix else 0}")
        return None
    
    # 4. Génération du Treillis
    print("--- Génération du Treillis ... ---")
    lattice = context.lattice
    
    return lattice

# --- EXÉCUTION ---
if __name__ == "__main__":
    # Assurez-vous que le chemin est correct
    path = 'sortie.rcft'
    
    lattice = generate_lattice_from_rcft(path)

    if lattice:
        print("\n--- RÉSULTATS : CONCEPTS IDENTIFIÉS ---")
        for concept in lattice:
            # On récupère les objets et attributs
            extent = list(concept.extent)
            intent = list(concept.intent)
            
            # Affichage propre
            print(f"Concept : {extent}")
            print(f"   └── Propriétés : {intent}")
            
            # Détection simple d'abstraction
            if len(extent) > 1 and len(intent) > 0:
                print("   [!] CANDIDAT ABSTRACTION (A envoyer au LLM)")
            print("-" * 30)