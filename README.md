# Zebedee

**NOTE: We are currently in the process of deprecating this service due to:**
 - _Performance limitations_
 - _Maintainability issues_
 - _Inability to scalability effectively_
***

Zebedee is the CMS used by the ONS website and the internal website publishing system. It is a JSON API and does not 
have a user interface. It comes in 2 flavours:

## zebedee-reader
Zebedee-reader is read-only. It's used by [Babbage][1] (the public facing web frontend of the ONS website) it returns 
the published site content as JSON.

## zebedee-cms
Zebedee-cms is an extension of zebedee-reader. It's used by [Florence][2] and provides API endpoints for managing 
content, users, teams and publishing collecions. Zebedee-CMS is not public facing and requires authentication for the 
majority functionality. Pre-release content is encrypted and requries the appropriate permissions to be able to 
access it.

## Prerequisites 
- git
- Java 8
- Maven
- Docker

Zebedee is JSON API and does not have a user interface. The quickest and easiest way to use it is to set up a local copy
of the "publishing" stack. Clone and set up the following projects following the README instructions in each repo:
- [Florence][2]
- [Babbage][1]
- [Sixteens][5]
- [dp-compose][6] 

### Getting started
***
_If you encounter any issues or notice anything missing from this guide please update/add any missing/helpful
 information and open a PR._

_Much appreciated._
_The Dev team_
***
_NOTE_: The following set guide will set up Zebedee in **"CMS"** mode as this is typically how the devlopers will run 
the stack locally. 

Getting the code

```
git clone git@github.com:ONSdigital/zebedee.git
```


### Database... 
Zebedee isn't backed by a database instead it uses a file system to store json files on disk ***. As a result it 
requires a specific directory structure in order to function correctly.

***
*** _We know this is a terrible idea - but in our defence this is a legacy hangover and we are actively working 
towards deprecating it._
***

To save yourself some pain you can use the [dp-zebedee-utils/content][3] tool to create the required directory 
structure and populate the CMS with some "default content" - follow the steps in the [README][3] before going any further.
 
Once the script has run successfully copy the generated script from `dp-zebedee-utils/content/generated/run-cms.sh` into
the root dir of your Zebedee project. This bash script compiles and runs Zebedee setting typical dev default 
configuration and uses the content / directory structure generated by dp-zebedee-utils.

You may be required to make the bash script an executable before you can run it. If so run:

````bash
sudo chmod +x run-cms.sh
<Enter you password when prompted>
````  

### Running the publishing stack
In order to use Zebedee you will need to have the following other project running:
- Florence
- Babbage
- Sixteens
- dp-compose

Follow the steps in the README of each project.

#### Running zebedee 
```bash
./run-cms.sh
```

Assuming Zebedee has started without error head to [Florence login][4] and login with the default account:
```
email: florence@magicroundabout.ons.gov.uk
password: Doug4l
```
- If it's the first time logging in you will be prompted to change the password for that user.
- On the home screen create a new collection.
- Click `Create/edit` on the Collection Details screen.

If everything is working correctly you should now see the the ONS website displayed in the right hand pane. 
_Congratulattions_ :tada:! Advanced to GO collect £200 :dollar:

Otherwise :violin: kindly ask someone from the dev team to help troubleshoot.


[1]: https://github.com/ONSdigital/babbage
[2]: https://github.com/ONSdigital/florence
[3]: https://github.com/ONSdigital/dp-zebedee-utils/tree/master/content
[4]: http://localhost:8081/florence/login
[5]: https://github.com/ONSdigital/sixteens
[6]: https://github.com/ONSdigital/dp-compose
