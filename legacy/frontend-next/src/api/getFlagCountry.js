export const getFlagCountry = async (name) => {
    try {
        const response = await fetch(`https://restcountries.com/v3.1/name/${name}`);
        const country = await response.json();
        const flagUrl = country[0].flags.png;

        return flagUrl;
    } catch (error) {
        console.error('Error al obtener la bandera del país:', error);
        
        return null;
    }
} 