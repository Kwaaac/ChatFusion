# ChatFusion
Server fusion and clients communication protocol -- Java 18 preview

# Subject

Ce document décrit le protocole ChatFusion. Le protocole permet à des clients de communiquer avec un serveur ChatFusion. Les serveurs ChatFusion se comporte comme des serveurs de discussion classiques. L'originalité de ce protocole est de permettre à plusieurs serveurs de fusionner pour se comporter comme un seul serveur fusionné. L'ensemble des communications entre clients et serveurs se feront au dessus de TCP.


Fonctionnement général et terminologie :
----------------------------------------

Chaque serveur possède un nom qui ne doit pas dépasser 96 octets une fois encodé en UTF8, ce nom est fixé pour toute la durée de vie du serveur.

Un "méga-serveur" désigne un ensemble de serveurs ChatFusion qui ont fusionné. Le leader d'un méga-serveur est le serveur ayant le plus petit nom dans l'ordre lexicographique (on compare d'abord les noms par leur taille et par l'ordre du dictionnaire). Si un serveur ChatFusion n'a jamais fusionné, il est un méga-serveur à lui tout seul et il est donc le leader.

Le protocole ChatFusion va assurer qu'il y a une connexion TCP entre chaque membre du méga-serveur et le leader du méga-serveur. Toutes les communications entre les serveurs d'un méga-serveur se feront par le leader. Les membres d'un méga-serveur connaissent l'adresse de la socket du leader.

Représentation des données :
----------------------------
## Opcode
Nous avons représenter les OpCodes sous la forme d'une énumération. Chaque énumération contenant alors 2 champs. 
Un int associant le nom du code avec un numéro, et une instance de Reader qui permet de lire la request associé.
## BufferSerializable
Cette interface nous permet de mettre au format buffer encodé en UTF8 ce qui l'implémente

## Request
Nous avons défini les communications entre sevrer et client comme des Request. Pour cela, nous avons matérialisé 
chacune de ses communications par une implémentation de BufferSerializable, nous permettant de mettre chacune de ces 
requêtes dans des buffers simplement. 

Chacune de ces classes est une implémentation de l'interface Request qui renvoie 
un OpCodeCes classes ne définissent pas le comportement du client ou du serveur quant à la réception de ces request

## Wrapper
Nous avons des classes wrapper qui nous permet de wrapper des Strings et des adresses, ces wrapper implémente l'interface 
BufferSerializable et permet dans les records Request de pouvoir directement utiliser ses wrapper plutôt que les classes 
classiques, on évite le surplus de code inutile pour ajouter les Integer.BYTES pour les tailles des Strings par exemple
## Reader
Nous avons créé des reader pour chaque Request, à chaque fois, c'est un reader de Request (cela sert pour le Design Pattern Visitor)

## Pattern Matching
Plutôt que de mettre en place un design pattern Visitor qui, pour nous, est très lourd à mettre en place.Nous avons 
choisi d'utiliser le preview de java 18 sur le pattern matching pour mettre en place le pattern visitor de façon plus 
adéquate selon nous.

Dans le processIn du client et du serveur, nous arborons la même sémantique initiale. Si nous ne sommes 
pas en train de lire une requête, nous lisons une nouvelle requête. En fonction du byte obtenu, nous utilisons un reader associé.

Grâce au sous-typage, nous pouvons utiliser un reader de Request et avoir le même comportement.Une fois la request lu 
en entier par le requestReader, nous pouvons gérer la request obtenu grâce au pattern matching.Nous avons alors un switch 
qui va pouvoir déterminer de quelle requête il s'agit et d'effectuer du code en conséquence.Nous pouvons alors, pour
un client et un serveur implémenter leurs manières à eux de gérer une requête, sans avoir de code mort dans une requête 
gérer par un sevrer, mais pas par un client.

## Gestion des fichiers 

Pour la gestion de fichiers, nous avons mis en place une nouvelle classe qui permette de garder en mémoire l'envoie 
ou la réception d'un fichier.

Nous pouvons alors garder une map des fichiers entrain d'être téléchargée, dès lors, lorsque les données arrivent en décaler
,nous pouvons continuer le téléchargement.Du même type, il y a au sein du processOut, une priorité au sein de l'envoi des 
requêtes de fichiers, l'envoie de fichier étant moins prioritaire, si plusieurs requêtes doivent être inscrites dans le buffer, 
celles qui envoient un fichier seront inscritent en dernier.



