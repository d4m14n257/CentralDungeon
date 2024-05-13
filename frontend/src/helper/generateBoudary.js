export function generateBoudary() {
    let str = "";
    const length = Math.floor(Math.random() * 11) + 30;
    const charSet = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    for (let i = 0; i < length; i++) {
        const randomIndex = Math.floor(Math.random() * charSet.length);
        str += charSet.charAt(randomIndex);
    }
    return str;
}