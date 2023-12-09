import ModalBase from "../ModalBase";

export default function NestedCommentModal (props) {
    const { isOpen, handleCloseModal } = props;

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            Si abri aqui xd
        </ModalBase>
    );
}