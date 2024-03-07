import CardComponent from "@/components/CardComponent";

const table = {
    name: 'Nombre de una mesa xd',
    master: 'Teshinyl mal escrito'
}

export default function Joined () {
    return (
        <CardComponent 
            title={table.name}
            subtitle={table.master}
        />
    );
}