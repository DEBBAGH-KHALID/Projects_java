# Trois petits projets Java Swing

Ce dépôt regroupe trois applications que j'ai écrites en Java avec Swing, sans aucune bibliothèque externe : un clone de Paint, un casse-briques et une simulation de station-service. Chaque projet tient dans un seul fichier `.java`, donc c'est facile à lire, à lancer et à modifier.

Je les ai faits pour apprendre : dessin 2D, événements souris et clavier, boucle de jeu, animation avec un `Timer`. Si ça peut servir de base à quelqu'un d'autre, tant mieux.

---

## 1. Paint

Un clone de Microsoft Paint. On peut vraiment dessiner, enregistrer et rouvrir ses images.

**Ce que ça sait faire**
- 11 outils : crayon, pinceau, gomme, pot de peinture, pipette, ligne, rectangle, ellipse, rectangle arrondi, texte et sélection
- Une palette de 20 couleurs, plus un sélecteur pour en choisir d'autres
- Clic gauche pour la couleur principale, clic droit pour la couleur de fond
- Réglage de l'épaisseur du trait et option « remplir la forme »
- Annuler / rétablir, couper / copier / coller, tout sélectionner
- Zoom avant, arrière et retour à 100 %
- Redimensionner la zone de dessin, retourner l'image (horizontalement ou verticalement), la pivoter de 90°, l'effacer
- Ouvrir et enregistrer des images (via `ImageIO`)
- Glisser-déposer d'une image dans la fenêtre
- Interface en français, anglais et espagnol, changeable à la volée

**Lancer**
```bash
javac Paint.java
java Paint
```

---

## 2. Break Bricks

Un casse-briques classique avec quelques options en plus pour s'amuser.

**Le principe** : renvoyer la balle avec la raquette, casser toutes les briques, passer au niveau suivant. Il y a 5 niveaux et 3 vies. La balle accélère un peu à chaque niveau, et l'angle de rebond dépend de l'endroit de la raquette que vous touchez, donc on peut viser.

**Commandes**

| Touche | Action |
|---|---|
| Flèches gauche / droite, ou A / D, ou la souris | Déplacer la raquette |
| Espace ou clic | Lancer la balle / rejouer |
| P | Pause |
| Flèche haut / bas (ou + / −) | Augmenter / diminuer la vitesse |
| B | Ajouter une balle (jusqu'à 50 en même temps) |

Le jeu tourne dans son propre thread avec un pas de temps adapté, pour que la vitesse reste la même même si l'ordinateur ralentit un peu.

**Lancer**
```bash
javac BreakBricks.java
java BreakBricks
```

---

## 3. Station Gazoil

Ce n'est pas un jeu mais une simulation. On regarde la vie d'une station-service : des voitures arrivent, font la queue, prennent du gazoil à la pompe, puis repartent. Quand le réservoir est vide, un camion-citerne arrive pour le remplir.

**Ce qu'on peut voir**
- Un réservoir de 1000 litres qui se vide au fil des services
- Une file d'attente de voitures, chacune avec sa couleur et la quantité de gazoil qu'elle demande
- Les voitures qui patientent avec un message « Plus de gazoil... » quand la cuve est à sec
- Le camion qui arrive, décharge, puis repart
- Un tableau de bord avec le niveau du réservoir, le nombre de voitures servies, la file d'attente et le nombre de livraisons

**Les contrôles** (en bas de la fenêtre)
- **Pause / Reprendre** : fige la simulation
- **Vider le réservoir** : pratique pour voir tout de suite arriver le camion
- **Affluence des voitures** : un curseur de 1 à 10 pour régler le trafic

**Lancer**
```bash
javac StationGazoil.java
java StationGazoil
```

---

## Ce qu'il faut pour les faire tourner

- Un JDK récent (Java 17 ou plus, ça marche très bien). On le trouve sur [adoptium.net](https://adoptium.net)
- Rien d'autre : tout est dans la bibliothèque standard de Java

Vous pouvez aussi ouvrir les fichiers dans IntelliJ IDEA, Eclipse ou NetBeans et lancer la classe qui contient le `main`.

## Pistes pour aller plus loin

- Paint : ajouter la sauvegarde dans plusieurs formats, un outil courbe ou polygone, une gomme avec aperçu
- Break Bricks : bonus qui tombent des briques, briques plus résistantes, sauvegarde du meilleur score
- Station Gazoil : plusieurs pompes, des prix, des statistiques sur la journée

