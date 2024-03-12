import ModalBase from "@/components/ModalBase";

export default function EditModalTable (props) {
    const {isOpen, handleCloseModal} = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            prueba de editar xd
        </ModalBase>
    );
}