export async function getLanguage(language) {
    var select;

    switch(language) {
        case 'spanish': {
            select = await fetch('src/language/spanish.xml');
            break;
        }
        case 'english': {
            select = await fetch('src/language/english.xml');
            break;
        }
    }

    const languageParser = new DOMParser().parseFromString(select, "text/xml");
    console.log(language, languageParser, select)
}